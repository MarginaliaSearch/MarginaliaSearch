package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingDetails;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.ranking.results.ResultValuator;
import nu.marginalia.sequence.GammaCodedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.sql.SQLException;
import java.util.*;

@Singleton
public class IndexResultValuatorService {
    private static final Logger logger = LoggerFactory.getLogger(IndexResultValuatorService.class);

    private final IndexMetadataService metadataService;
    private final DocumentDbReader documentDbReader;
    private final ResultValuator resultValuator;
    private final StatefulIndex statefulIndex;

    @Inject
    public IndexResultValuatorService(IndexMetadataService metadataService,
                                      DocumentDbReader documentDbReader,
                                      ResultValuator resultValuator,
                                      StatefulIndex statefulIndex)
    {
        this.metadataService = metadataService;
        this.documentDbReader = documentDbReader;
        this.resultValuator = resultValuator;
        this.statefulIndex = statefulIndex;
    }

    public List<SearchResultItem> rankResults(SearchParameters params,
                                              ResultRankingContext rankingContext,
                                              CombinedDocIdList resultIds)
    {
        IndexResultValuationContext evaluator =
                new IndexResultValuationContext(resultValuator, statefulIndex, rankingContext, params);

        List<SearchResultItem> results = new ArrayList<>(resultIds.size());

        try (var arena = Arena.ofConfined()) {
            // Batch-fetch the word metadata for the documents

            var searchTerms = metadataService.getSearchTerms(params.compiledQuery, params.query);
            var termsForDocs = metadataService.getTermMetadataForDocuments(arena, resultIds, searchTerms.termIdsAll);

            // Prepare data for the document.  We do this outside of the calculation function to avoid
            // hash lookups in the inner loop, as it's very hot code and we don't want thrashing in there;
            // out here we can rely on implicit array ordering to match up the data.

            var ra = resultIds.array();
            long[] flags = new long[searchTerms.termIdsAll.size()];
            GammaCodedSequence[] positions = new GammaCodedSequence[searchTerms.termIdsAll.size()];

            for (int i = 0; i < ra.length; i++) {
                long id = ra[i];

                // Prepare term-level data for the document
                for (int ti = 0; ti < flags.length; ti++) {
                    long tid = searchTerms.termIdsAll.at(ti);
                    var tfd = termsForDocs.get(tid);

                    assert tfd != null : "No term data for term " + ti;

                    flags[ti] = tfd.flag(i);
                    positions[ti] = tfd.position(i);
                }

                // Calculate the preliminary score

                var score = evaluator.calculatePreliminaryScore(id, searchTerms, flags, positions);
                if (score != null) {
                    results.add(score);
                }
            }

            return results;
        }
    }


    public List<DecoratedSearchResultItem> selectBestResults(SearchParameters params,
                                                     Collection<SearchResultItem> results) throws SQLException {

        var domainCountFilter = new IndexResultDomainDeduplicator(params.limitByDomain);

        List<SearchResultItem> resultsList = new ArrayList<>(results.size());

        for (var item : results) {
            if (domainCountFilter.test(item)) {
                // It's important that this filter runs across all results, not just the top N
                if (resultsList.size() < params.limitTotal) {
                    resultsList.add(item);
                }
            }
        }

        for (var item : resultsList) {
            item.resultsFromDomain = domainCountFilter.getCount(item);
        }

        return decorateResults(resultsList, params.compiledQuery);
    }

    /** Decorate the result items with additional information from the link database
     * and calculate an updated ranking with the additional information */
    public List<DecoratedSearchResultItem> decorateResults(List<SearchResultItem> rawResults,
                                                           CompiledQuery<String> compiledQuery)
            throws SQLException
    {
        TLongList idsList = new TLongArrayList(rawResults.size());

        for (var result : rawResults)
            idsList.add(result.getDocumentId());

        Map<Long, DocdbUrlDetail> urlDetailsById = new HashMap<>(rawResults.size());

        for (var item : documentDbReader.getUrlDetails(idsList))
            urlDetailsById.put(item.urlId(), item);

        List<DecoratedSearchResultItem> resultItems = new ArrayList<>(rawResults.size());
        for (var result : rawResults) {
            var id = result.getDocumentId();
            var docData = urlDetailsById.get(id);

            if (docData == null) {
                logger.warn("No document data for id {}", id);
                continue;
            }

            resultItems.add(createCombinedItem(
                    result,
                    docData));
        }
        return resultItems;
    }

    private DecoratedSearchResultItem createCombinedItem(SearchResultItem result,
                                                         DocdbUrlDetail docData) {

        ResultRankingDetailsExtractor detailsExtractor = new ResultRankingDetailsExtractor();
       //  Consumer<ResultRankingDetails> detailConsumer = rankingContext.params.exportDebugData ? detailsExtractor::set : null;

        return new DecoratedSearchResultItem(
                result,
                docData.url(),
                docData.title(),
                docData.description(),
                docData.urlQuality(),
                docData.format(),
                docData.features(),
                docData.pubYear(),
                docData.dataHash(),
                docData.wordsTotal(),
                0L, //bestPositions(wordMetas),
                result.getScore(),
                detailsExtractor.get()
        );
    }

    private static class ResultRankingDetailsExtractor {
        private ResultRankingDetails value = null;

        public ResultRankingDetails get() {
            return value;
        }
        public void set(ResultRankingDetails value) {
            this.value = value;
        }
    }

    private long bestPositions(CompiledQueryLong wordMetas) {
        LongSet positionsSet = CompiledQueryAggregates.positionsAggregate(wordMetas, WordMetadata::decodePositions);

        int bestPc = 0;
        long bestPositions = 0;

        var li = positionsSet.longIterator();

        while (li.hasNext()) {
            long pos = li.nextLong();
            int pc = Long.bitCount(pos);
            if (pc > bestPc) {
                bestPc = pc;
                bestPositions = pos;
            }
        }

        return bestPositions;
    }
}
