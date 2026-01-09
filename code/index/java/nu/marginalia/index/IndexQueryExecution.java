package nu.marginalia.index;

import io.prometheus.metrics.core.metrics.Gauge;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.RankableDocument;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.reverse.query.IndexQuery;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.piping.*;
import nu.marginalia.skiplist.SkipListConstants;
import nu.marginalia.skiplist.SkipListReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.sql.SQLException;
import java.time.Duration;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.*;

/** Performs an index query */
public class IndexQueryExecution {

    private static final boolean printDebugSummary = Boolean.getBoolean("index.printDebugSummary");

    private static final int maxSimultaneousQueries = Integer.getInteger("index.maxSimultaneousQueries", 4);
    private static final Semaphore simultaneousRequests = new Semaphore(maxSimultaneousQueries);

    private static final int lookupBatchSize = SkipListConstants.MAX_RECORDS_PER_BLOCK;

    private static final ExecutorService threadPool =
            new ThreadPoolExecutor(16, 256, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    private static final Logger log = LoggerFactory.getLogger(IndexQueryExecution.class);

    private final String nodeName;

    private final CombinedIndexReader currentIndex;
    private final IndexResultRankingService rankingService;

    private final SearchContext rankingContext;
    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;
    private final ResultPriorityQueue resultHeap;

    private final BufferPipe<IndexQuery> processingPipe;

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

    class LookupStage implements BufferPipe.IntermediateFunction<IndexQuery, CombinedDocIdList> {
        final LongQueryBuffer buffer = new LongQueryBuffer(lookupBatchSize);

        @Override
        public void process(IndexQuery query, PipeDrain<CombinedDocIdList> output) throws Exception {
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
            buffer.dispose();
        }
    }


    class PreparationStage implements BufferPipe.IntermediateFunction<CombinedDocIdList, RankableDocument> {

        @Override
        public void process(CombinedDocIdList docIds, PipeDrain<RankableDocument> output) throws Exception {

            long[] termIds = rankingContext.termIdsAll.array;

            BitSet[] priorityTermsPresentDocWise = new BitSet[rankingContext.termIdsPriority.size()];
            for (int i = 0; i < rankingContext.termIdsPriority.size(); i++) {
                priorityTermsPresentDocWise[i] = currentIndex
                        .getValuePresence(rankingContext, rankingContext.termIdsPriority.getLong(i), docIds);
            }

            SkipListReader.ValueReader[] readers = new SkipListReader.ValueReader[termIds.length];
            SkipListReader.ValueReader firstViableReader = null;

            for (int i = 0; i < termIds.length; i++) {
                if (null != (readers[i] = currentIndex.getValueReader(rankingContext, termIds[i], docIds))) {
                    firstViableReader = readers[i];
                }
            }
            if (firstViableReader == null) {
                return;
            }

            for (;;) {
                long[] positionOffsets = new long[termIds.length];
                long[] metadata = new long[termIds.length];

                boolean hasAny = false;

                for (int i = 0; i < readers.length; i++) {
                    if (readers[i] == null || !readers[i].advance()) {
                        positionOffsets[i] = metadata[i] = 0L;
                        continue;
                    }

                    hasAny = true;
                    positionOffsets[i] = readers[i].getValue(0);
                    metadata[i] = readers[i].getValue(1);
                }
                if (!hasAny) break;

                int docIdx = firstViableReader.getIndex();
                long docId = docIds.at(docIdx);

                if (!isViable(metadata))
                    continue;

                RankableDocument item = new RankableDocument(docId);

                // strip to term flags
                for (int i = 0; i < metadata.length; i++) {
                    metadata[i] &= 0xFFL;
                }

                item.positionOffsets = positionOffsets;
                item.termFlags = metadata;
                item.priorityTermsPresent = new boolean[rankingContext.termIdsPriority.size()];

                for (int i = 0; i < rankingContext.termIdsPriority.size(); i++) {
                    if (priorityTermsPresentDocWise[i].get(docIdx))
                        item.priorityTermsPresent[i] = true;
                }

                if (!output.accept(item))
                    break;
            }

        }


        private boolean isViable(long[] metadata) {

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

                    if ((value & 0xFF) == 0)
                        thisMask &= value;
                }

                combinedMasks |= thisMask;
                bestFlagsCount = Math.max(bestFlagsCount, minFlagCount);
            }

            return combinedMasks != 0L || bestFlagsCount > 0;
        }

    }

    class RankingStage implements BufferPipe.FinalFunction<RankableDocument> {
        ScratchIntListPool pool = new ScratchIntListPool(64);

        @Override
        public void process(RankableDocument rankableDocument) throws Exception {
            try (var arena = Arena.ofConfined()) {
                rankableDocument.positions = currentIndex.getTermPositions(arena, rankableDocument.positionOffsets);
                rankableDocument.documentSpans = currentIndex.getDocumentSpans(arena, rankableDocument.combinedDocumentId);

                if (null != (rankableDocument.item = rankingService.calculateScore(null, pool, currentIndex, rankingContext, rankableDocument))) {
                    resultHeap.add(rankableDocument);
                }
            }
        }
    }

    public IndexQueryExecution(CombinedIndexReader currentIndex,
                               IndexResultRankingService rankingService,
                               SearchContext rankingContext,
                               int serviceNode) {
        this.currentIndex = currentIndex;
        this.nodeName = Integer.toString(serviceNode);
        this.rankingService = rankingService;
        this.rankingContext = rankingContext;

        resultHeap = new ResultPriorityQueue(rankingContext.limitTotal * 2);

        budget = rankingContext.budget;
        limitByDomain = rankingContext.limitByDomain;
        limitTotal = rankingContext.limitTotal;

        queries = currentIndex.createQueries(rankingContext);

        processingPipe = BufferPipeBuilder.<IndexQuery>of(threadPool)
                .addStage("Lookup", 32, 4, LookupStage::new)
                .addStage("Processing", 16, 4, PreparationStage::new)
                .finalStage("Ranking", 16, 8, RankingStage::new);

    }

    public List<RpcDecoratedResultItem> run() throws InterruptedException, SQLException, TooManySimultaneousQueriesException {


        if (!simultaneousRequests.tryAcquire(budget.timeLeft() / 2, TimeUnit.MILLISECONDS)) {
            index_execution_rejected_queries.inc();
            throw new TooManySimultaneousQueriesException();
        }
        try {
            for (IndexQuery query : queries) {
                if (!budget.hasTimeLeft())
                    break;
                processingPipe.offer(query, Duration.ofMillis(budget.timeLeft()));
            }
            processingPipe.stopFeeding();

            if (!processingPipe.join(budget.timeLeft())) {
                processingPipe.stop();
                processingPipe.join();
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
        return rankingService.selectBestResults(limitByDomain, limitTotal, rankingContext, resultHeap.toList());

    }

    public int itemsProcessed() {
        return resultHeap.getItemsProcessed();
    }

}
