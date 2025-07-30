package nu.marginalia.index.index;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.index.FullReverseIndexReader;
import nu.marginalia.index.PrioReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.TermMetadataList;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    public CombinedIndexReader(ForwardIndexReader forwardIndexReader,
                               FullReverseIndexReader reverseIndexFullReader,
                               PrioReverseIndexReader reverseIndexPriorityReader) {
        this.forwardIndexReader = forwardIndexReader;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPriorityReader = reverseIndexPriorityReader;
    }

    public IndexQueryBuilderImpl newQueryBuilder(IndexQuery query) {
        return new IndexQueryBuilderImpl(reverseIndexFullReader, query);
    }

    public QueryFilterStepIf hasWordFull(long termId) {
        return reverseIndexFullReader.also(termId);
    }

    /** Creates a query builder for terms in the priority index */
    public IndexQueryBuilder findPriorityWord(long wordId) {
        return newQueryBuilder(new IndexQuery(reverseIndexPriorityReader.documents(wordId)))
                .withSourceTerms(wordId);
    }

    /** Creates a query builder for terms in the full index */
    public IndexQueryBuilder findFullWord(long wordId) {
        return newQueryBuilder(
                new IndexQuery(reverseIndexFullReader.documents(wordId)))
                .withSourceTerms(wordId);
    }

    /** Creates a parameter matching filter step for the provided parameters */
    public QueryFilterStepIf filterForParams(QueryParams params) {
        return new ParamMatchingQueryFilter(params, forwardIndexReader);
    }

    /** Returns the number of occurrences of the word in the full index */
    public int numHits(long word) {
        return reverseIndexFullReader.numDocuments(word);
    }

    /** Reset caches and buffers */
    public void reset() {
        reverseIndexFullReader.reset();
    }

    public List<IndexQuery> createQueries(SearchTerms terms, QueryParams params) {

        if (!isLoaded()) {
            logger.warn("Index reader not ready");
            return Collections.emptyList();
        }

        List<IndexQueryBuilder> queryHeads = new ArrayList<>(10);

        final long[] termPriority = terms.sortedDistinctIncludes(this::compareKeywords);
        List<LongSet> paths = CompiledQueryAggregates.queriesAggregate(terms.compiledQuery());

        // Remove any paths that do not contain all prioritized terms, as this means
        // the term is missing from the index and can never be found
        paths.removeIf(containsAll(termPriority).negate());

        for (var path : paths) {
            LongList elements = new LongArrayList(path);

            elements.sort((a, b) -> {
                for (int i = 0; i < termPriority.length; i++) {
                    if (termPriority[i] == a)
                        return -1;
                    if (termPriority[i] == b)
                        return 1;
                }
                return 0;
            });

            if (!SearchTerms.stopWords.contains(elements.getLong(0))) {
                var head = findFullWord(elements.getLong(0));

                for (int i = 1; i < elements.size(); i++) {
                    long termId = elements.getLong(i);

                    // if a stop word is present in the query, skip the step of requiring it to be in the document,
                    // we'll assume it's there and save IO
                    if (SearchTerms.stopWords.contains(termId)) {
                        continue;
                    }

                    head.addInclusionFilter(hasWordFull(termId));
                }
                queryHeads.add(head);
            }

            // If there are few paths, we can afford to check the priority index as well
            if (paths.size() < 4) {
                var prioHead = findPriorityWord(elements.getLong(0));
                for (int i = 1; i < elements.size(); i++) {
                    prioHead.addInclusionFilter(hasWordFull(elements.getLong(i)));
                }
                queryHeads.add(prioHead);
            }
        }

        // Add additional conditions to the query heads
        for (var query : queryHeads) {

            // Advice terms are a special case, mandatory but not ranked, and exempt from re-writing
            for (long term : terms.advice()) {
                query = query.also(term);
            }

            for (long term : terms.excludes()) {
                query = query.not(term);
            }

            // Run these filter steps last, as they'll worst-case cause as many page faults as there are
            // items in the buffer
            query.addInclusionFilter(filterForParams(params));
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

    private int compareKeywords(long a, long b) {
        return Long.compare(
                numHits(a),
                numHits(b)
        );
    }
    /** Returns the number of occurrences of the word in the priority index */
    public int numHitsPrio(long word) {
        return reverseIndexPriorityReader.numDocuments(word);
    }

    /** Retrieves the term metadata for the specified word for the provided documents */
    public TermMetadataList getTermMetadata(Arena arena,
                                            long wordId,
                                            CombinedDocIdList docIds)
    {
        return new TermMetadataList(reverseIndexFullReader.getTermData(arena, wordId, docIds.array()));
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
    public DocumentSpans[] getDocumentSpans(Arena arena, CombinedDocIdList docIds) {
        long[] decodedIDs = docIds.array();
        for (int i = 0; i < decodedIDs.length; i++) {
            decodedIDs[i] = UrlIdCodec.removeRank(decodedIDs[i]);
        }

        return forwardIndexReader.getDocumentSpans(arena, decodedIDs);
    }

    /** Close the indexes (this is not done immediately)
     * */
    public void close() {
       /* Delay the invocation of close method to allow for a clean shutdown of the service.
        *
        * This is especially important when using Unsafe-based LongArrays, since we have
        * concurrent access to the underlying memory-mapped file.  If pull the rug from
        * under the caller by closing the file, we'll get a SIGSEGV.  Even with MemorySegment,
        * we'll get ugly stacktraces if we close the file while a thread is still accessing it.
        */

        delayedCall(forwardIndexReader::close, Duration.ofMinutes(1));
        delayedCall(reverseIndexFullReader::close, Duration.ofMinutes(1));
        delayedCall(reverseIndexPriorityReader::close, Duration.ofMinutes(1));
    }


    private void delayedCall(Runnable call, Duration delay) {
        Thread.ofPlatform().start(() -> {
            try {
                TimeUnit.SECONDS.sleep(delay.toSeconds());
                call.run();
            } catch (InterruptedException e) {
                logger.error("Interrupted", e);
            }
        });
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
    public boolean test(long combinedId) {
        long docId = UrlIdCodec.removeRank(combinedId);
        int domainId = UrlIdCodec.getDomainId(docId);

        if (!validateDomain(domainId)) {
            return false;
        }

        if (!imposesMetaConstraint) {
            return true;
        }

        long meta = forwardIndexReader.getDocMeta(docId);

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

    private boolean validateDomain(int domainId) {
        return params.searchSet().contains(domainId);
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