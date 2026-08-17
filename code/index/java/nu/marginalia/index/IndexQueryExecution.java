package nu.marginalia.index;

import io.prometheus.metrics.core.metrics.Gauge;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.compiled.CqDataLong;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.debug.DebugRankingFactors;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.ffi.IoUring;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.results.snippet.SnippetGenerator;
import nu.marginalia.index.reverse.query.IndexQuery;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.piping.BufferPipe;
import nu.marginalia.piping.PipeDrain;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.skiplist.SkipListReader;
import nu.marginalia.skiplist.ValueBatchContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/** Performs an index query */
public class IndexQueryExecution {

    private static final Logger logger = LoggerFactory.getLogger(IndexQueryExecution.class);

    private static final boolean printDebugSummary = Boolean.getBoolean("index.printDebugSummary");
    private static final boolean disableViabilityPrecheck = Boolean.getBoolean("index.disableViabilityPrecheck");

    private static final int rankingBatchSize = Integer.getInteger("index.rankingBatchSize", 32);

    private static final int preparationConcurrency = Integer.getInteger("index.preparationConcurrency", 4);
    private static final int rankingConcurrency = Integer.getInteger("index.rankingConcurrency", 8);

    /** Fetch per document data for the ranking stage with batched io_uring reads
     *  rather than serial mmap and pread accesses per document */
    private static final boolean useUringFetch = IoUring.isAvailable
            && !Boolean.getBoolean("index.disableUringFetch");

    /** Read the value blocks a preparation batch needs in one io_uring submission
     *  rather than one blocking pread per block */
    private static final boolean useBatchValueReads = IoUring.isAvailable
            && Boolean.parseBoolean(System.getProperty("index.batchValueReads", "true"));

    private static final int maxSimultaneousQueries = Integer.getInteger("index.maxSimultaneousQueries", 8);
    private static final Semaphore simultaneousRequests = new Semaphore(maxSimultaneousQueries);

    private static final int lookupBatchSize = 512;

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    private final DocumentDbReader documentDbReader;
    private final String nodeName;

    private final CombinedIndexReader currentIndex;
    private final IndexResultRankingService rankingService;

    private final SearchContext rankingContext;
    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;
    private final ResultPriorityQueue resultHeap;

    private final int limitTotal;
    private final int limitByDomain;

    private static final Gauge metric_index_documents_ranked = Gauge.builder()
            .labelNames("node")
            .name("index_exec_documents_ranked")
            .help("Number of documents ranked")
            .register();

    private static final Gauge index_execution_rejected_queries = Gauge.builder()
            .labelNames("node")
            .name("index_execution_rejected_queries")
            .help("Number of queries rejected to avoid backpressure")
            .register();

    public static class TooManySimultaneousQueriesException extends Exception {
        @Override
        public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[0];
        }
    }

    public IndexQueryExecution(CombinedIndexReader currentIndex,
                               DocumentDbReader documentDbReader,
                               IndexResultRankingService rankingService,
                               SearchContext rankingContext,
                               int serviceNode) {
        this.currentIndex = currentIndex;
        this.documentDbReader = documentDbReader;
        this.nodeName = Integer.toString(serviceNode);
        this.rankingService = rankingService;
        this.rankingContext = rankingContext;

        resultHeap = new ResultPriorityQueue(rankingContext.limitTotal, rankingContext.limitByDomain);

        budget = rankingContext.budget;
        limitByDomain = rankingContext.limitByDomain;
        limitTotal = rankingContext.limitTotal;

        queries = currentIndex.createQueries(rankingContext);

    }

    public List<RpcDecoratedResultItem> run() throws InterruptedException, SQLException, TooManySimultaneousQueriesException {

        if (!simultaneousRequests.tryAcquire(budget.timeLeft() / 2, TimeUnit.MILLISECONDS)) {
            index_execution_rejected_queries
                    .labelValues(nodeName)
                    .inc();
            throw new TooManySimultaneousQueriesException();
        }

        try (BufferPipe<IndexQuery> processingPipe = BufferPipe.<IndexQuery>builder(threadPool, Duration.ofSeconds(1))
                .addStage("Lookup", 32, queries.size(), LookupStage::new)
                .addStage("Deduplicate", 16, 1, DeduplicateStage::new)
                .addStage("Processing", 16, preparationConcurrency, PreparationStage::new)
                .finalStage("Ranking", 16, rankingConcurrency, RankingStage::new))
        {

            for (IndexQuery query : queries) {
                processingPipe.offer(query, Duration.ofMillis(budget.timeLeft()));
            }

            processingPipe.stopFeeding();

            if (!processingPipe.join(budget.timeLeft())) {
                processingPipe.stop();
            }
        }
        finally {
            simultaneousRequests.release();
        }

        if (printDebugSummary) {
            for (var query : queries) {
                query.printDebugInformation();
            }
        }

        metric_index_documents_ranked
                .labelValues(nodeName)
                .inc(1000. * resultHeap.getItemsProcessed() / budget.getLimitTime());

        // Final result selection
        List<RankableDocument> resultsList = new ArrayList<>(resultHeap.size());
        LongList idsList = new LongArrayList(limitTotal);

        for (RankableDocument item : resultHeap) {
            if (resultsList.size() >= limitTotal) {
                break;
            }

            int domainId = item.domainId();

            resultsList.add(item);
            item.resultsFromDomain = resultHeap.numResultsFromDomain(domainId);
            idsList.add(item.item.getDocumentId());
        }

        // If we're exporting debug data from the ranking, we need to re-run the ranking calculation
        // for the selected results, as this would be comically expensive to do for all the results we
        // discard along the way

        if (rankingContext.params.getExportDebugData()) {
            // Re-rank the results while gathering debugging data
            performDebugRanking(rankingContext, resultsList);
        }

        // Fetch the document details for the selected results in one go, from the local document database
        // for this index partition
        Map<Long, DocdbUrlDetail> detailsById = documentDbReader.getUrlDetails(idsList);

        List<RpcDecoratedResultItem> ret = new ArrayList<>(resultsList.size());

        // Decorate the results with the document details
        try (SnippetGenerator snippetGenerator = new SnippetGenerator(currentIndex, rankingContext)) {
            ResultConverter converter = new ResultConverter();
            for (RankableDocument doc : resultsList) {

                final long id = doc.item.getDocumentId();
                final DocdbUrlDetail docData = detailsById.get(id);

                if (docData == null)
                    continue;

                String snippet = snippetGenerator.generate(doc);
                int pubDate = currentIndex.getDocPubDate(doc.item.combinedId);

                converter.convert(rankingContext, doc, docData, snippet, pubDate)
                        .ifPresent(ret::add);
            }
        }

        return ret;
    }


    private class LookupStage implements BufferPipe.IntermediateFunction<IndexQuery, CombinedDocIdList> {
        final LongQueryBuffer buffer = new LongQueryBuffer(lookupBatchSize);
        final Lock indexLock = currentIndex.useLock();

        public LookupStage() {
            if (!indexLock.tryLock()) {
                throw new IllegalStateException("Index lock could not be acquired");
            }
        }


        @Override
        public void process(IndexQuery query, PipeDrain<CombinedDocIdList> output) {
            while (query.hasMore() && budget.hasTimeLeft()) {
                buffer.zero();

                query.getMoreResults(buffer);

                if (buffer.isEmpty())
                    continue;

                if (!output.accept(new CombinedDocIdList(buffer)))
                    break;
            }
        }

        @Override
        public void cleanUp() {
            try {
                buffer.dispose();
            }
            finally {
                indexLock.unlock();
            }
        }
    }


    private class DeduplicateStage implements BufferPipe.IntermediateFunction<CombinedDocIdList, CombinedDocIdList> {
        private static final ConcurrentLinkedQueue<LongOpenHashSet> setPool = new ConcurrentLinkedQueue<>();

        private final LongOpenHashSet seen;

        DeduplicateStage() {
            LongOpenHashSet pooled = setPool.poll();
            seen = pooled != null ? pooled : new LongOpenHashSet(100_000);
        }

        @Override
        public void cleanUp() {
            seen.clear();
            setPool.add(seen);
        }

        @Override
        public void process(CombinedDocIdList input, PipeDrain<CombinedDocIdList> output) {

            if (input.isEmpty())
                return;

            long[] items = input.array();
            int n = 0;
            for (int in = 0; in < items.length; in++) {
                long id = items[in];
                if (seen.add(id)) {
                    items[n++] = id;
                }
            }

            output.accept(new CombinedDocIdList(items, 0, n));
        }

    }


    private class PreparationStage implements BufferPipe.IntermediateFunction<CombinedDocIdList, RankableDocument[]> {
        // Slabs are pooled and bounded by peak stage concurrency, so they can be
        // sized generously to keep allocations out of the overflow path
        private static final ScratchSegmentAllocatorFactory allocatorFactory
                = new ScratchSegmentAllocatorFactory("Preparation", 1 << 20);

        /** Contexts hold a ring on the value file, so they outlive the stage and
         *  are pooled, and are discarded when the index they were opened against
         *  is swapped out */
        private static final ConcurrentLinkedQueue<ValueBatchContext> batchContextPool = new ConcurrentLinkedQueue<>();
        private static final AtomicBoolean warnedBatchContextFailure = new AtomicBoolean();

        private final ScratchSegmentAllocator segmentAllocator;

        @Nullable
        private final ValueBatchContext batchContext;

        private final Lock indexLock = currentIndex.useLock();

        final BitSet[] priorityTermsPresentDocWise = new BitSet[rankingContext.termIdsPriority.size()];
        final long[] termIds = rankingContext.termIdsAll.array;
        final SkipListReader.ValueReader[] readers = new SkipListReader.ValueReader[termIds.length];

        final long[] positionOffsets = new long[termIds.length];
        final long[] metadata = new long[termIds.length];

        public PreparationStage() {
            if (!indexLock.tryLock()) {
                throw new IllegalStateException("Index lock could not be acquired");
            }

            try {
                segmentAllocator = allocatorFactory.createAllocator();
                batchContext = useBatchValueReads ? claimBatchContext() : null;
            }
            catch (RuntimeException e) {
                indexLock.unlock();
                throw new IllegalStateException("Failed to create value batch context", e);
            }
        }

        /** Contexts are tied to the value file's descriptor, so a pooled one built
         *  against a swapped out index must be discarded.  Returns null if none can
         *  be had, in which case value blocks are read one at a time. */
        @Nullable
        private ValueBatchContext claimBatchContext() {
            ValueBatchContext pooled;
            while ((pooled = batchContextPool.poll()) != null) {
                if (pooled.owner() == currentIndex.valueReaderIdentity()) {
                    return pooled;
                }
                pooled.close();
            }

            try {
                return currentIndex.createValueBatchContext();
            }
            catch (RuntimeException e) {
                if (!warnedBatchContextFailure.getAndSet(true)) {
                    logger.warn("Failed to create value batch context, using serial reads", e);
                }
                return null;
            }
        }


        @Override
        public void process(CombinedDocIdList docIds, PipeDrain<RankableDocument[]> output) throws IOException {


            /** Create bit sets for the priority terms */
            for (int i = 0; i < rankingContext.termIdsPriority.size(); i++) {
                priorityTermsPresentDocWise[i] = currentIndex
                        .getValuePresence(rankingContext, rankingContext.termIdsPriority.getLong(i), docIds);
            }


            /** Create value readers for the regular terms */
            SkipListReader.ValueReader anyReader = null;
            for (int i = 0; i < termIds.length; i++) {
                if (null != (readers[i] = currentIndex.getValueReader(rankingContext, segmentAllocator, termIds[i], docIds, batchContext))) {
                    anyReader = readers[i];
                }
            }

            if (anyReader == null) {
                // No viable readers, we can do nothing with this docIds list
                return;
            }

            try {
                RankableDocument[] batch = new RankableDocument[rankingBatchSize];
                int batchLen = 0;

                for (;;) {
                    boolean hasData = false;

                    for (int i = 0; i < readers.length; i++) {
                        if (readers[i] == null || !readers[i].advance()) {
                            positionOffsets[i] = metadata[i] = 0L;
                            continue;
                        }

                        hasData = true;
                        anyReader = readers[i];
                        positionOffsets[i] = readers[i].getValue(0);
                        metadata[i] = readers[i].getValue(1);
                    }

                    if (!hasData) break;

                    int docIdx = anyReader.getIndex();
                    long docId = docIds.at(docIdx);

                    if (!isViable(metadata))
                        continue;

                    /** Create rankable document */

                    RankableDocument item = new RankableDocument(docId);

                    // strip to term flags
                    for (int i = 0; i < metadata.length; i++) {
                        metadata[i] &= 0xFFL;
                    }

                    item.positionOffsets = Arrays.copyOf(positionOffsets, positionOffsets.length);
                    item.termFlags = Arrays.copyOf(metadata,  metadata.length);

                    item.priorityTermsPresent = new boolean[rankingContext.termIdsPriority.size()];

                    for (int i = 0; i < rankingContext.termIdsPriority.size(); i++) {
                        if (priorityTermsPresentDocWise[i].get(docIdx))
                            item.priorityTermsPresent[i] = true;
                    }

                    batch[batchLen++] = item;
                    if (batchLen == batch.length) {
                        if (!output.accept(batch)) {
                            return;
                        }
                        batch = new RankableDocument[rankingBatchSize];
                        batchLen = 0;
                    }
                }

                if (batchLen > 0) {
                    output.accept(Arrays.copyOf(batch, batchLen));
                }
            }
            finally {
                segmentAllocator.reset();
            }

        }


        private boolean isViable(long[] metadata) {
            if (disableViabilityPrecheck)
                return true;

            long combinedMasks = 0L;
            long bestFlagsCount = 0L;

            for (IntList path : rankingContext.compiledQueryIds.paths) {
                long thisMask = ~0L;
                long minFlagCount = Integer.MAX_VALUE;

                for (int pathIdx : path) {
                    long value = metadata[pathIdx];

                    minFlagCount = Math.min(minFlagCount, Long.bitCount((value & 0xFF)));

                    if (WordFlags.Synthetic.isPresent((byte) value))
                        continue;
                    if (WordFlags.UrlDomain.isPresent((byte) value))
                        continue;
                    if (WordFlags.UrlPath.isPresent((byte) value))
                        continue;

                    if ((value & 0xFF) != 0)
                        thisMask &= value;
                }

                combinedMasks |= thisMask;
                bestFlagsCount = Math.max(bestFlagsCount, minFlagCount);
            }

            return combinedMasks != 0L || bestFlagsCount > 0;
        }

        @Override
        public void cleanUp() {
            try {
                if (batchContext != null) {
                    batchContextPool.add(batchContext);
                }
                indexLock.unlock();
            }
            finally {
                segmentAllocator.close();
            }

        }
    }

    private class RankingStage implements BufferPipe.FinalFunction<RankableDocument[]> {

        private static final ScratchSegmentAllocatorFactory allocatorFactory
                = new ScratchSegmentAllocatorFactory("Ranking", 1 << 20);
        private static final ConcurrentLinkedQueue<RankingBatchFetcher> fetcherPool = new ConcurrentLinkedQueue<>();
        private static final AtomicBoolean warnedFetcherFailure = new AtomicBoolean();

        // per-thread instances
        private final ScratchIntListPool pool = new ScratchIntListPool(64);
        private final ScratchSegmentAllocator segmentAllocator;
        private final RankingBatchFetcher fetcher;
        private final ResultPriorityQueue localResults = new ResultPriorityQueue(rankingContext.limitTotal, rankingContext.limitByDomain);

        private final Lock indexLock = currentIndex.useLock();

        public RankingStage() {
            if (!indexLock.tryLock()) {
                throw new IllegalStateException("Index lock could not be acquired");
            }

            try {
                segmentAllocator = allocatorFactory.createAllocator();
                fetcher = useUringFetch ? claimFetcher() : null;
            }
            catch (RuntimeException e) {
                indexLock.unlock();
                throw new IllegalStateException("Failed to create batch fetcher", e);
            }
        }

        /** Fetchers hold rings on the index files, so pooled instances built
         *  against a swapped out index must be discarded, not reused.  Returns null
         *  if a fetcher cannot be constructed, in which case the stage falls back
         *  to the serial fetch path. */
        @Nullable
        private RankingBatchFetcher claimFetcher() {
            RankingBatchFetcher pooled;
            while ((pooled = fetcherPool.poll()) != null) {
                if (pooled.owner() == currentIndex) {
                    return pooled;
                }
                pooled.close();
            }

            try {
                return new RankingBatchFetcher(currentIndex);
            }
            catch (RuntimeException e) {
                if (!warnedFetcherFailure.getAndSet(true)) {
                    logger.warn("Failed to create batch fetcher, using serial reads", e);
                }
                return null;
            }
        }

        @Override
        public void process(RankableDocument[] batch) {
            if (fetcher != null) {
                processBatched(batch);
            }
            else {
                for (RankableDocument rankableDocument : batch) {
                    try {
                        fetchSerially(rankableDocument);
                        scoreDocument(rankableDocument);
                    }
                    finally {
                        pool.reset();
                        segmentAllocator.reset();
                    }
                }
            }
        }

        /** All reads for the batch happen in three batched submissions, then the
         *  documents are scored without touching the index files */
        private void processBatched(RankableDocument[] batch) {
            try {
                long[] spansEncoded = fetcher.fetchEntries(batch);
                DecodableDocumentSpans[] codedSpans = fetcher.fetchSpans(spansEncoded, segmentAllocator);
                MemorySegment[][] positionSegments = fetcher.fetchPositionSegments(batch, segmentAllocator);

                fetcher.positionsDecoder().decodeBatch(positionSegments);

                for (int i = 0; i < batch.length; i++) {
                    RankableDocument rankableDocument = batch[i];
                    try {
                        if (codedSpans[i] == null) continue;

                        rankableDocument.documentSpans = codedSpans[i].decode(pool::get);
                        rankableDocument.positions = fetcher.positionsDecoder().positionsForDocument(positionSegments[i], i, pool);

                        scoreDocument(rankableDocument);
                    }
                    finally {
                        pool.reset();
                    }
                }
            }
            finally {
                segmentAllocator.reset();
            }
        }

        private void fetchSerially(RankableDocument rankableDocument) {
            rankableDocument.docMetadata = currentIndex.getDocumentMetadata(rankableDocument.combinedDocumentId);
            rankableDocument.htmlFeatures = currentIndex.getHtmlFeatures(rankableDocument.combinedDocumentId);
            rankableDocument.docSize = currentIndex.getDocumentSize(rankableDocument.combinedDocumentId);

            DocumentSpans spans = getSpans(rankableDocument.combinedDocumentId);
            if (spans == null) {
                return;
            }

            rankableDocument.documentSpans = spans;
            rankableDocument.positions = getPositions(rankableDocument.positionOffsets);
        }

        private void scoreDocument(RankableDocument rankableDocument) {
            if (rankableDocument.documentSpans == null) {
                return;
            }

            SearchResultItem resultItem = rankingService.calculateScore(
                    null, pool, currentIndex, rankingContext, rankableDocument);

            if (null != resultItem) {
                rankableDocument.item = resultItem;
                localResults.add(rankableDocument);
            }
        }

        @Nullable
        private DocumentSpans getSpans(long combinedDocumentId) {
            DecodableDocumentSpans codedSpans = currentIndex.getDocumentSpans(segmentAllocator, combinedDocumentId);

            if (codedSpans == null)
                return null;

            return codedSpans.decode(pool::get);
        }

        @NotNull
        private IntList[] getPositions(long[] positionOffsets) {
            CodedSequence[] codedPositions = currentIndex.getTermPositions(segmentAllocator, positionOffsets);
            IntList[] ret = new IntList[codedPositions.length];

            for (int i = 0; i < ret.length; i++) {
                if (codedPositions[i] != null) {
                    ret[i] = codedPositions[i].values(pool::get);
                }
                else {
                    ret[i] = IntList.of();
                }
            }
            return ret;
        }

        @Override
        public void cleanUp() {
            try {
                // Add our results to the shared pool of results
                synchronized (resultHeap) {
                    resultHeap.addAll(localResults);
                }
            }
            finally {
                try {
                    if (fetcher != null) {
                        fetcher.flushMetrics();
                        fetcherPool.add(fetcher);
                    }
                    segmentAllocator.close();
                }
                finally {
                    indexLock.unlock();
                }
            }
        }
    }

    public int itemsProcessed() {
        return resultHeap.getItemsProcessed();
    }

    /** Rank the results again, gathering detailed ranking information */
    private void performDebugRanking(SearchContext searchContext, List<RankableDocument> results) {

        // The term data the documents held during ranking lives in recycled
        // scratch buffers by now and must be fetched anew

        ScratchIntListPool pool = new ScratchIntListPool(128);
        ScratchSegmentAllocator segmentAllocator = RankingStage.allocatorFactory.createAllocator();

        try {
            for (var doc : results) {
                pool.reset();
                segmentAllocator.reset();

                DecodableDocumentSpans codedSpans = currentIndex.getDocumentSpans(segmentAllocator, doc.combinedDocumentId);
                if (codedSpans == null)
                    continue;

                doc.documentSpans = codedSpans.decode(pool::get);

                CodedSequence[] codedPositions = currentIndex.getTermPositions(segmentAllocator, doc.positionOffsets);
                IntList[] positions = new IntList[codedPositions.length];
                for (int i = 0; i < positions.length; i++) {
                    if (codedPositions[i] != null) {
                        positions[i] = codedPositions[i].values(pool::get);
                    }
                    else {
                        positions[i] = IntList.of();
                    }
                }
                doc.positions = positions;

                SearchResultItem score = rankingService.calculateScore(new DebugRankingFactors(), pool, currentIndex, searchContext, doc);

                if (score != null) {
                    doc.item = score;
                }
            }
        }
        finally {
            segmentAllocator.close();
        }
    }


    private static class ResultConverter {
        private final LongOpenHashSet seenDocumentHashes = new LongOpenHashSet();

        @Nullable
        public Optional<RpcDecoratedResultItem> convert(SearchContext rankingContext,
                                                 RankableDocument doc,
                                                 DocdbUrlDetail docData,
                                                 @Nullable String snippet,
                                                 int pubDate) {
            SearchResultItem resultItem = doc.item;

            // Filter out duplicates by content
            if (!seenDocumentHashes.add(docData.dataHash())) {
                return Optional.empty();
            }

            var rawItem = RpcRawResultItem.newBuilder();

            rawItem.setCombinedId(resultItem.combinedId);
            rawItem.setHtmlFeatures(resultItem.htmlFeatures);
            rawItem.setEncodedDocMetadata(resultItem.encodedDocMetadata);
            rawItem.setHasPriorityTerms(resultItem.hasPrioTerm);

            for (var score : resultItem.keywordScores) {
                rawItem.addKeywordScores(
                        RpcResultKeywordScore.newBuilder()
                                .setFlags(score.flags)
                                .setPositions(score.positionCount)
                                .setKeyword(score.keyword)
                );
            }

            var decoratedBuilder = RpcDecoratedResultItem.newBuilder()
                    .setDataHash(docData.dataHash())
                    .setDescription(Objects.requireNonNullElse(snippet, ""))
                    .setFeatures(docData.features())
                    .setFormat(docData.format())
                    .setRankingScore(resultItem.getScore())
                    .setTitle(docData.title())
                    .setUrl(docData.url().toString())
                    .setUrlQuality(docData.urlQuality())
                    .setWordsTotal(docData.wordsTotal())
                    .setBestPositions(resultItem.getBestPositions())
                    .setResultsFromDomain(doc.resultsFromDomain)
                    .setRawItem(rawItem);

            if (docData.pubYear() != null) {
                decoratedBuilder.setPubYear(docData.pubYear());
            }
            decoratedBuilder.setPubDate(pubDate);

            if (resultItem.debugRankingFactors != null) {
                decoratedBuilder.setRankingDetails(createDebugInformation(rankingContext, resultItem));
            }

            return Optional.of(decoratedBuilder.build());
        }

    }



    private static RpcResultRankingDetails.Builder createDebugInformation(SearchContext searchContext, SearchResultItem resultItem) {
        var debugFactors = resultItem.debugRankingFactors;
        var detailsBuilder = RpcResultRankingDetails.newBuilder();
        var documentOutputs = RpcResultDocumentRankingOutputs.newBuilder();

        for (var factor : debugFactors.getDocumentFactors()) {
            documentOutputs.addFactor(factor.factor());
            documentOutputs.addValue(factor.value());
        }

        detailsBuilder.setDocumentOutputs(documentOutputs);

        var termOutputs = RpcResultTermRankingOutputs.newBuilder();

        CqDataLong termIds = searchContext.compiledQueryIds.data;

        for (var entry : debugFactors.getTermFactors()) {
            String term = "[ERROR IN LOOKUP]";

            // CURSED: This is a linear search, but the number of terms is small, and it's in a debug path
            for (int i = 0; i < termIds.size(); i++) {
                if (termIds.get(i) == entry.termId()) {
                    term = searchContext.compiledQuery.at(i);
                    break;
                }
            }

            termOutputs
                    .addTermId(entry.termId())
                    .addTerm(term)
                    .addFactor(entry.factor())
                    .addValue(entry.value());
        }

        detailsBuilder.setTermOutputs(termOutputs);

        return detailsBuilder;
    }

}
