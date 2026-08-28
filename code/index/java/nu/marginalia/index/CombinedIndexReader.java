package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.*;
import nu.marginalia.api.searchquery.model.query.SpecificationLimitType;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.config.ForwardIndexParameters;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.forward.doctext.DocTextDecoder;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.model.*;
import nu.marginalia.index.reverse.FullReverseIndexReader;
import nu.marginalia.index.reverse.IndexLanguageContext;
import nu.marginalia.index.reverse.PrioReverseIndexReader;
import nu.marginalia.index.reverse.query.IndexQuery;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.skiplist.SkipListReader;
import nu.marginalia.skiplist.SkipListValueReader;
import nu.marginalia.skiplist.ValueBatchContext;
import nu.marginalia.skiplist.SkipListValueRanges;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** A reader for the combined forward and reverse indexes.
 * <p></p>
 * This class does not deal with the lifecycle of the indexes,
 * that is the responsibility of {@link StatefulIndex}.
 * */
public class CombinedIndexReader {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ForwardIndexReader forwardIndexReader;
    private final FullReverseIndexReader reverseIndexFullReader;
    private final PrioReverseIndexReader reverseIndexPriorityReader;

    private final ReadWriteLock leaseLock = new ReentrantReadWriteLock();

    public final Lock useLock() {
        return leaseLock.readLock();
    }
    public final Lock closeLock() {
        return leaseLock.writeLock();
    }

    public CombinedIndexReader(ForwardIndexReader forwardIndexReader,
                               FullReverseIndexReader reverseIndexFullReader,
                               PrioReverseIndexReader reverseIndexPriorityReader) {
        this.forwardIndexReader = forwardIndexReader;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPriorityReader = reverseIndexPriorityReader;
    }

    public IndexLanguageContext createLanguageContext(String languageIsoCode) {
        return new IndexLanguageContext(languageIsoCode,
                reverseIndexFullReader.getWordLexicon(languageIsoCode),
                reverseIndexPriorityReader.getWordLexicon(languageIsoCode)
        );
    }

    /** Returns the number of occurrences of the word in the full index */
    public int numHits(IndexLanguageContext languageContext, long term) {
        return reverseIndexFullReader.numDocuments(languageContext, term);
    }

    /** Reset caches and buffers */
    public void reset() {
        reverseIndexFullReader.reset();
    }

    public List<IndexQuery> createQueries(SearchContext context) {

        if (!isLoaded()) {
            logger.warn("Index reader not ready");
            return Collections.emptyList();
        }

        final IndexLanguageContext languageContext = context.languageContext;

        @Nullable
        SkipListValueRanges mandatoryDocumentRanges = context.mandatoryDomainIds.isEmpty() ? null : getDocumentRangesForDomains(context.mandatoryDomainIds);

        @Nullable
        SkipListValueRanges excludedDocumentRanges = context.excludedDomainIds.isEmpty() ? null : getDocumentRangesForDomains(context.excludedDomainIds);

        List<TermSlotGroup> groups = TermSlotGroup.fromPaths(context.compiledQueryIds, context.compiledQuery.variantClasses);

        List<IndexQueryBuilder> queryHeads = new ArrayList<>(10);

        for (TermSlotGroup group : groups) {
            for (TermSlotGroup.Plan plan : group.plan(termId -> numHits(languageContext, termId), context.termFreqDocCount())) {
                addQueryHeads(queryHeads, context, false, plan.head(), plan.filters(), mandatoryDocumentRanges);
                addQueryHeads(queryHeads, context, true, plan.head(), plan.filters(), mandatoryDocumentRanges);
            }
        }

        // Add additional conditions to the query heads
        for (var query : queryHeads) {

            if (excludedDocumentRanges != null) query.rejectingDomains(excludedDocumentRanges);

            // Require terms are a special case, mandatory but not ranked, and exempt from re-writing
            for (long termId : context.termIdsRequire) {
                query = query.also(termName(context, termId), termId, context.budget);
            }

            for (long termId : context.termIdsExcludes) {
                query = query.not(termName(context, termId), termId, context.budget);
            }

            // Run these filter steps last, as they'll worst-case cause as many page faults as there are
            // items in the buffer

            query.addInclusionFilter(filterForParams(context.queryParams));
        }

        return queryHeads
                .stream()
                .map(IndexQueryBuilder::build)
                .toList();
    }

    private void addQueryHeads(List<IndexQueryBuilder> queryHeads,
                               SearchContext context,
                               boolean priority,
                               long headTerm,
                               List<LongList> filterSlots,
                               @Nullable SkipListValueRanges mandatoryDocumentRanges)
    {
        if (mandatoryDocumentRanges != null || context.termIdsDomain.isEmpty()) {
            IndexQueryBuilder head = findWord(context, priority, headTerm, mandatoryDocumentRanges);
            if (!head.isNoOp()) {
                addSlotFilters(head, context, filterSlots);
                queryHeads.add(head);
            }
        }

        if (!context.termIdsDomain.isEmpty()) {
            IndexQueryBuilder head = findWord(context, priority, headTerm, null);
            if (!head.isNoOp()) {
                addSlotFilters(head, context, filterSlots);
                head.addInclusionFilter(hasAnyWordFull(context.languageContext, termNames(context, context.termIdsDomain), context.termIdsDomain, context.budget));
                queryHeads.add(head);
            }
        }
    }

    private IndexQueryBuilder findWord(SearchContext context,
                                       boolean priority,
                                       long termId,
                                       @Nullable SkipListValueRanges ranges)
    {
        String term = termName(context, termId);

        if (!priority) {
            return findFullWord(context.languageContext, ranges, term, termId);
        }
        else {
            IndexQueryBuilder head = findPriorityWord(context.languageContext, term, termId);

            // The priority index has no range filtered source, so the restriction is applied as a filter instead
            if (ranges != null) {
                head.requiringDomains(ranges);
            }

            return head;
        }
    }

    private void addSlotFilters(IndexQueryBuilder head, SearchContext context, List<LongList> slots) {
        for (LongList slot : slots) {
            if (slot.size() == 1) {
                long termId = slot.getLong(0);
                head.addInclusionFilter(hasWordFull(context.languageContext, termName(context, termId), termId, context.budget));
            }
            else {
                head.addInclusionFilter(hasAnyWordFull(context.languageContext, termNames(context, slot), slot, context.budget));
            }
        }
    }

    private static String termName(SearchContext context, long termId) {
        return context.termIdToString.getOrDefault(termId, "???");
    }

    private static List<String> termNames(SearchContext context, LongList termIds) {
        List<String> names = new ArrayList<>(termIds.size());
        for (long termId : termIds) {
            names.add(termName(context, termId));
        }
        return names;
    }

    public List<IndexQuery> createUnrankedQueries(UnrankedSearchContext context) {

        if (!isLoaded()) {
            logger.warn("Index reader not ready");
            return Collections.emptyList();
        }

        final IndexLanguageContext languageContext = context.languageContext;

        final long[] termSortOrder = context.sortedDistinctIncludes((a,b) -> Long.compare(
                numHits(languageContext, a),
                numHits(languageContext, b)
        ));

        LongList searchTerms = new LongArrayList(context.termIdsRequireUnique);

        // Sort in order of size in the index
        searchTerms.sort((a, b) -> {
            for (long l : termSortOrder) {
                if (l == a)
                    return -1;
                if (l == b)
                    return 1;
            }
            return 0;
        });

        Long2ObjectOpenHashMap<String> termIdToString = context.termIdToString;

        IndexQueryBuilder head;

        long firstTermId = searchTerms.getLong(0);
        String firstTerm = termIdToString.getOrDefault(firstTermId, "???");

        if (context.afterCombinedDocId != 0)
            head = findFullWord(languageContext,
                    getDocumentRangesAfterDocId(context.afterCombinedDocId),
                    firstTerm,
                    firstTermId);
        else
            head = findFullWord(languageContext,
                    null,
                    firstTerm,
                    firstTermId);

        if (head.isNoOp()) {
            return List.of();
        }

        if (!context.excludedDomainIds.isEmpty())
            head.rejectingDomains(getDocumentRangesForDomains(context.excludedDomainIds));
        if (!context.mandatoryDomainIds.isEmpty())
            head.requiringDomains(getDocumentRangesForDomains(context.mandatoryDomainIds));

        for (long termId : context.termIdsRequire) {
            head = head.also(termIdToString.getOrDefault(termId, "???"), termId, context.budget);
        }

        for (long termId : context.termIdsExcludes) {
            head = head.not(termIdToString.getOrDefault(termId, "???"), termId, context.budget);
        }

        return List.of(head.build());
    }

    /** Returns the number of occurrences of the word in the priority index */
    public int numHitsPrio(IndexLanguageContext languageContext, long word) {
        return reverseIndexPriorityReader.numDocuments(languageContext, word);
    }


    public IndexQueryBuilder newQueryBuilder(IndexLanguageContext context, IndexQuery query) {
        return new IndexQueryBuilder(reverseIndexFullReader, context, query);
    }

    public QueryFilterStepIf hasWordFull(IndexLanguageContext languageContext, String term, long termId, IndexSearchBudget budget) {
        return reverseIndexFullReader.also(languageContext, term, termId, budget);
    }

    public QueryFilterStepIf hasAnyWordFull(IndexLanguageContext languageContext, List<String> terms, LongList termIds, IndexSearchBudget budget) {
        return reverseIndexFullReader.any(languageContext, terms, termIds, budget);
    }

    /** Creates a query builder for terms in the priority index */
    public IndexQueryBuilder findPriorityWord(IndexLanguageContext languageContext, String term, long termId) {
        IndexQuery query = new IndexQuery(reverseIndexPriorityReader.documents(languageContext, term, termId), true);

        return newQueryBuilder(languageContext, query).withSourceTerms(termId);
    }

    /** Creates a query builder for terms in the full index */
    public IndexQueryBuilder findFullWord(IndexLanguageContext languageContext, String term, long termId) {
        IndexQuery query = new IndexQuery(reverseIndexFullReader.documents(languageContext, term, termId), false);

        return newQueryBuilder(languageContext, query).withSourceTerms(termId);
    }

    /** Creates a query builder for terms in the full index */
    public IndexQueryBuilder findFullWord(IndexLanguageContext languageContext,
                                          @Nullable SkipListValueRanges ranges,
                                          String term,
                                          long termId) {

        if (null == ranges || ranges.isEmpty()) return findFullWord(languageContext, term, termId);

        IndexQuery query = new IndexQuery(reverseIndexFullReader.documents(languageContext, ranges, term, termId), false);

        return newQueryBuilder(languageContext, query).withSourceTerms(termId);
    }

    private SkipListValueRanges getDocumentRangesForDomains(@NotNull IntList domainIds) {
        long[] rangesStarts = new long[domainIds.size()];
        long[] rangesEnds = new long[domainIds.size()];

        for (int i = 0; i < domainIds.size(); i++) {
            rangesStarts[i] = forwardIndexReader.getRankEncodedDocumentIdBase(domainIds.getInt(i));
            rangesEnds[i] = rangesStarts[i] + UrlIdCodec.DOCORD_COUNT;
        }

        return new SkipListValueRanges(rangesStarts, rangesEnds);
    }

    private SkipListValueRanges getDocumentRangesAfterDocId(long combinedDocId) {

        long start = combinedDocId + 1;
        long end = Long.MAX_VALUE;

        return new SkipListValueRanges(new long[] { start }, new long[] { end });
    }

    /** Creates a parameter matching filter step for the provided parameters */
    public QueryFilterStepIf filterForParams(QueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
    }

    @Nullable
    @CheckReturnValue
    public SkipListReader.ValueReader getValueReader(SearchContext searchContext,
                                                     SegmentAllocator allocator,
                                                     long termId,
                                                     CombinedDocIdList keys,
                                                     @Nullable ValueBatchContext batchContext) {
        return reverseIndexFullReader.getValueReader(searchContext, allocator, termId, keys, batchContext);
    }

    @Nullable
    public ValueBatchContext createValueBatchContext() {
        return reverseIndexFullReader.createValueBatchContext();
    }

    /** The value reader a batch context would be opened against, for checking
     *  whether a pooled context still belongs to the live index */
    @Nullable
    public SkipListValueReader valueReaderIdentity() {
        return reverseIndexFullReader.valueReaderIdentity();
    }

    public BitSet getValuePresence(SearchContext searchContext, long termId, CombinedDocIdList keys) {
        return reverseIndexFullReader.getValuePresence(searchContext, termId, keys);
    }

    /** Retrieves the document metadata for the specified document */
    public long getDocumentMetadata(long combinedDocId) {
        return forwardIndexReader.getDocMeta(combinedDocId);
    }

    /** Returns the total number of documents in the index */
    public int totalDocCount() {
        return forwardIndexReader.totalDocCount();
    }

    /** Retrieves the HTML features for the specified document */
    public int getHtmlFeatures(long combinedDocId) {
        return forwardIndexReader.getHtmlFeatures(combinedDocId);
    }

    /** Retrieves the HTML features for the specified document */
    public int getDocumentSize(long docId) {
        return forwardIndexReader.getDocumentSize(docId);
    }

    public int getDocPubDate(long docId) {
        return forwardIndexReader.getDocPubDate(docId);
    }

    /** File descriptors and entry offsets for the batched ranking fetch path */

    public int forwardDataFd() {
        return forwardIndexReader.dataFd();
    }

    public int forwardSpansFd() {
        return forwardIndexReader.spansFd();
    }

    public int positionsFd() {
        return reverseIndexFullReader.positionsFd();
    }

    public long forwardDataOffsetForDoc(long combinedDocId) {
        return forwardIndexReader.dataOffsetForDoc(combinedDocId);
    }

    /** Mappings of the same files, for the fetch path that reads resident pages
     *  directly rather than copying them through the file descriptors */

    public MemorySegment mappedForwardData() {
        return forwardIndexReader.mappedData();
    }

    public MemorySegment mappedForwardSpans() {
        return forwardIndexReader.mappedSpans();
    }

    public MemorySegment mappedPositions() {
        return reverseIndexFullReader.mappedPositions();
    }

    public ForwardIndexParameters.ForwardIndexVersion forwardVersion() {
        return forwardIndexReader.version();
    }

    /** Retrieves the document spans for the specified documents */

    @Nullable
    public DecodableDocumentSpans getDocumentSpans(SegmentAllocator allocator, long documentId) {
        return forwardIndexReader.getDocumentSpans(allocator, documentId);
    }

    @Nullable
    public String getDocumentText(DocTextDecoder decoder, long documentId) {
        return forwardIndexReader.getDocumentText(decoder, documentId);
    }

    public CodedSequence[] getTermPositions(SegmentAllocator allocator, long[] codedOffsets) {
        return reverseIndexFullReader.getTermPositions(allocator, codedOffsets);
    }

    /** Close the indexes.  This blocks the calling thread until all users are finished.
     * */
    public boolean close() {
        var closeLock = closeLock();

        try {
            // Diagnostic for detecting if we have a read lock that is stuck or abandoned somewhere
            if (!closeLock.tryLock(10, TimeUnit.MINUTES)) {
                logger.error("Failed to acquire close lock");
                return false;
            }
        } catch (InterruptedException e) {
            logger.info("Interrupted while waiting for close lock", e);
        }

        // Holding the lock should guarantee this closes all fetchers
        RankingBatchFetcher.closeForIndex(this);

        try {
            forwardIndexReader.close();
        } catch (Throwable t) {
            logger.error("Failed to close forward index reader", t);
        }

        try {
            reverseIndexFullReader.close();
        } catch (Throwable t) {
            logger.error("Failed to close full reverse index reader", t);
        }

        try {
            reverseIndexPriorityReader.close();
        } catch (Throwable t) {
            logger.error("Failed to close prio reverse index reader", t);
        }

        // We don't unlock here, as the index is no longer readable ever
        return true;
    }

    /** Returns true if index data is available */
    public boolean isLoaded() {
        // We only need to check one of the readers, as they are either all loaded or none are
        return forwardIndexReader.isLoaded() && reverseIndexFullReader.isLoaded();
    }
}

class ParamMatchingQueryFilter implements QueryFilterStepIf {
    private final QueryParams params;
    private final ForwardIndexReader forwardIndexReader;
    private final boolean imposesMetaConstraint;

    public ParamMatchingQueryFilter(QueryParams params,
                                    ForwardIndexReader forwardIndexReader)
    {
        this.params = params;
        this.forwardIndexReader = forwardIndexReader;
        this.imposesMetaConstraint = params.imposesDomainMetadataConstraint();
    }

    @Override
    public void apply(LongQueryBuffer buffer) {
        if (!imposesMetaConstraint) {
            return;
        }

        while (buffer.hasMore()) {
            if (test(buffer.currentValue())) {
                buffer.retainAndAdvance();
            }
            else {
                buffer.rejectAndAdvance();
            }
        }

        buffer.finalizeFiltering();
    }

    public boolean test(long combinedId) {
        long meta = forwardIndexReader.getDocMeta(combinedId);

        if (!validateQuality(meta)) {
            return false;
        }

        if (!validateYear(meta)) {
            return false;
        }

        if (!validateSize(meta)) {
            return false;
        }

        if (!validateRank(meta)) {
            return false;
        }

        return true;
    }

    private boolean validateQuality(long meta) {
        final var limit = params.qualityLimit();

        if (limit.type() == SpecificationLimitType.NONE) {
            return true;
        }

        final int quality = DocumentMetadata.decodeQuality(meta);

        return limit.test(quality);
    }

    private boolean validateYear(long meta) {
        if (params.year().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeYear(meta);

        return params.year().test(postVal);
    }

    private boolean validateSize(long meta) {
        if (params.size().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeSize(meta);

        return params.size().test(postVal);
    }

    private boolean validateRank(long meta) {
        if (params.rank().type() == SpecificationLimitType.NONE)
            return true;

        int postVal = DocumentMetadata.decodeRank(meta);

        return params.rank().test(postVal);
    }

    @Override
    public double cost() {
        return 32;
    }

    @Override
    public String describe() {
        return getClass().getSimpleName();
    }

}