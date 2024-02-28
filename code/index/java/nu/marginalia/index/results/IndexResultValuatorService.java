package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.ranking.results.ResultValuator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        final var evaluator = createValuationContext(params, rankingContext, resultIds);

        List<SearchResultItem> results = new ArrayList<>(resultIds.size());

        for (long id : resultIds.array()) {
            var score = evaluator.calculatePreliminaryScore(id);
            if (score != null) {
                results.add(score);
            }
        }

        return results;
    }

    private IndexResultValuationContext createValuationContext(SearchParameters params,
                                                               ResultRankingContext rankingContext,
                                                               CombinedDocIdList resultIds)
    {
        return new IndexResultValuationContext(metadataService,
                resultValuator,
                resultIds,
                statefulIndex,
                rankingContext,
                params.subqueries,
                params.queryParams);
    }


    public List<DecoratedSearchResultItem> selectBestResults(SearchParameters params,
                                                     ResultRankingContext rankingContext,
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

        return decorateAndRerank(resultsList, rankingContext);
    }

    /** Decorate the result items with additional information from the link database
     * and calculate an updated ranking with the additional information */
    public List<DecoratedSearchResultItem> decorateAndRerank(List<SearchResultItem> rawResults,
                                                             ResultRankingContext rankingContext)
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

            resultItems.add(createCombinedItem(result, docData, rankingContext));
        }
        return resultItems;
    }

    private DecoratedSearchResultItem createCombinedItem(SearchResultItem result,
                                                         DocdbUrlDetail docData,
                                                         ResultRankingContext rankingContext) {
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
                resultValuator.calculateSearchResultValue(result.keywordScores, docData.wordsTotal(), rankingContext)
        );

    }
}
