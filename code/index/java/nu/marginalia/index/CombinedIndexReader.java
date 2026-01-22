package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.*;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.query.SpecificationLimitType;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.forward.ForwardIndexReader;
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
import nu.marginalia.skiplist.SkipListValueRanges;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

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
        final long[] termPriority = context.sortedDistinctIncludes((a,b) -> Long.compare(
            numHits(languageContext, a),
            numHits(languageContext, b)
        ));

        List<IndexQueryBuilder> queryHeads = new ArrayList<>(10);
        List<LongSet> paths = CompiledQueryAggregates.queriesAggregate(context.compiledQueryIds);

        // Remove any paths that do not contain all prioritized terms, as this means
        // the term is missing from the index and can never be found
        paths.removeIf(containsAll(termPriority).negate());

        Long2ObjectOpenHashMap<String> termIdToString = context.termIdToString;

        @Nullable
        SkipListValueRanges mandatoryDocumentRanges = context.mandatoryDomainIds.isEmpty() ? null : getDocumentRangesForDomains(context.mandatoryDomainIds);

        @Nullable
        SkipListValueRanges excludedDocumentRanges = context.excludedDomainIds.isEmpty() ? null : getDocumentRangesForDomains(context.excludedDomainIds);

        List<String> domainTerms = new ArrayList<>(context.termIdsDomain.size());
        for (long id : context.termIdsDomain) {
            domainTerms.add(termIdToString.getOrDefault(id, "???"));
        }

        for (var path : paths) {
            LongList elements = new LongArrayList(path);

            elements.sort((a, b) -> {
                for (long l : termPriority) {
                    if (l == a)
                        return -1;
                    if (l == b)
                        return 1;
                }
                return 0;
            });

            if (mandatoryDocumentRanges != null || context.termIdsDomain.isEmpty()) {
                IndexQueryBuilder head = findFullWord(languageContext, mandatoryDocumentRanges, termIdToString.getOrDefault(elements.getLong(0), "???"), elements.getLong(0));
                if (!head.isNoOp()) {
                    for (int i = 1; i < elements.size(); i++) {
                        head.addInclusionFilter(hasWordFull(languageContext, termIdToString.getOrDefault(elements.getLong(i), "???"), elements.getLong(i), context.budget));
                    }
                    queryHeads.add(head);
                }
            }
            if (!context.termIdsDomain.isEmpty()) {
                IndexQueryBuilder head = findFullWord(languageContext, null, termIdToString.getOrDefault(elements.getLong(0), "???"), elements.getLong(0));
                if (!head.isNoOp()) {
                    for (int i = 1; i < elements.size(); i++) {
                        head.addInclusionFilter(hasWordFull(languageContext, termIdToString.getOrDefault(elements.getLong(i), "???"), elements.getLong(i), context.budget));
                    }
                    head.addInclusionFilter(hasAnyWordFull(languageContext, domainTerms, context.termIdsDomain, context.budget));
                    queryHeads.add(head);
                }
            }

            // If there are few paths, we can afford to check the priority index as well
            if (paths.size() < 4 && context.termIdsDomain.size() < 4) {
                if (mandatoryDocumentRanges != null || context.termIdsDomain.isEmpty()) {
                    IndexQueryBuilder prioHead = findPriorityWord(languageContext, termIdToString.getOrDefault(elements.getLong(0), "???"), elements.getLong(0));
                    if (!prioHead.isNoOp()) {
                        for (int i = 1; i < elements.size(); i++) {
                            prioHead.addInclusionFilter(hasWordFull(languageContext, termIdToString.getOrDefault(elements.getLong(i), "???"), elements.getLong(i), context.budget));
                        }
                        if (mandatoryDocumentRanges != null) {
                            prioHead.requiringDomains(mandatoryDocumentRanges);
                        }
                        queryHeads.add(prioHead);
                    }
                }
                if (!context.termIdsDomain.isEmpty()) {
                    IndexQueryBuilder head = findPriorityWord(languageContext, termIdToString.getOrDefault(elements.getLong(0), "???"), elements.getLong(0));
                    if (!head.isNoOp()) {
                        for (int i = 1; i < elements.size(); i++) {
                            head.addInclusionFilter(hasWordFull(languageContext, termIdToString.getOrDefault(elements.getLong(i), "???"), elements.getLong(i), context.budget));
                        }
                        head.addInclusionFilter(hasAnyWordFull(languageContext, domainTerms, context.termIdsDomain, context.budget));
                        queryHeads.add(head);
                    }
                }
            }
        }

        // Add additional conditions to the query heads
        for (var query : queryHeads) {

            if (excludedDocumentRanges != null) query.rejectingDomains(excludedDocumentRanges);

            // Require terms are a special case, mandatory but not ranked, and exempt from re-writing
            for (long termId : context.termIdsRequire) {
                query = query.also(termIdToString.getOrDefault(termId, "???"), termId, context.budget);
            }

            for (long termId : context.termIdsExcludes) {
                query = query.not(termIdToString.getOrDefault(termId, "???"), termId, context.budget);
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

    private Predicate<LongSet> containsAll(long[] permitted) {
        LongSet permittedTerms = new LongOpenHashSet(permitted);
        return permittedTerms::containsAll;
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

    /** Creates a parameter matching filter step for the provided parameters */
    public QueryFilterStepIf filterForParams(QueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
    }

    @Nullable
    @CheckReturnValue
    public SkipListReader.ValueReader getValueReader(SearchContext searchContext,
                                                     long termId,
                                                     CombinedDocIdList keys) {
        return reverseIndexFullReader.getValueReader(searchContext, termId, keys);
    }

    public BitSet getValuePresence(SearchContext searchContext, long termId, CombinedDocIdList keys) {
        return reverseIndexFullReader.getValuePresence(searchContext, termId, keys);
    }

    /** Retrieves the document metadata for the specified document */
    public long getDocumentMetadata(long docId) {
        return forwardIndexReader.getDocMeta(docId);
    }

    /** Returns the total number of documents in the index */
    public int totalDocCount() {
        return forwardIndexReader.totalDocCount();
    }

    /** Retrieves the HTML features for the specified document */
    public int getHtmlFeatures(long docId) {
        return forwardIndexReader.getHtmlFeatures(docId);
    }

    /** Retrieves the HTML features for the specified document */
    public int getDocumentSize(long docId) {
        return forwardIndexReader.getDocumentSize(docId);
    }

    /** Retrieves the document spans for the specified documents */

    @Nullable
    public DecodableDocumentSpans getDocumentSpans(Arena arena, long documentId) {
        return forwardIndexReader.getDocumentSpans(arena, documentId);
    }

    public CodedSequence[] getTermPositions(Arena arena, long[] codedOffsets) {
        return reverseIndexFullReader.getTermPositions(arena, codedOffsets);
    }

    /** Close the indexes.  This blocks the calling thread until all users are finished.
     * */
    public void close() {
        closeLock().lock();

        forwardIndexReader.close();
        reverseIndexFullReader.close();
        reverseIndexPriorityReader.close();
    }

    /** Returns true if index data is available */
    public boolean isLoaded() {
        // We only need to check one of the readers, as they are either all loaded or none are
        return forwardIndexReader.isLoaded();
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