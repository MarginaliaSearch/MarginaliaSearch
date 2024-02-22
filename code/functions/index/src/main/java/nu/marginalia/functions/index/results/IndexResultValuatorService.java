package nu.marginalia.functions.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.functions.index.model.IndexSearchParameters;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.ranking.ResultValuator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class IndexResultValuatorService {
    private static final Logger logger = LoggerFactory.getLogger(IndexResultValuatorService.class);

    private final IndexMetadataService metadataService;
    private final DocumentDbReader documentDbReader;
    private final ResultValuator resultValuator;

    @Inject
    public IndexResultValuatorService(IndexMetadataService metadataService,
                                      DocumentDbReader documentDbReader,
                                      ResultValuator resultValuator) {
        this.metadataService = metadataService;
        this.documentDbReader = documentDbReader;
        this.resultValuator = resultValuator;
    }


    /** From a list of candidate document IDs, find the best results and decorate them with additional information */
    public List<DecoratedSearchResultItem> findBestResults(IndexSearchParameters params, ResultRankingContext rankingContext, TLongList resultIds) throws SQLException {

        final var evaluator = new IndexResultValuationContext(metadataService,
                resultIds,
                rankingContext,
                params.subqueries,
                params.queryParams);

        // Sort the ids for more favorable access patterns on disk
        resultIds.sort();

        // Parallel stream to calculate scores is a minor performance boost
        var results = Arrays.stream(resultIds.toArray())
                .parallel()
                .mapToObj(evaluator::calculatePreliminaryScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        results = selectBestResults(params, results);

        return decorateAndRerank(results, rankingContext);
    }

    private List<SearchResultItem> selectBestResults(IndexSearchParameters params, List<SearchResultItem> results) {

        var domainCountFilter = new IndexResultDomainDeduplicator(params.limitByDomain);

        results.sort(Comparator.naturalOrder());

        List<SearchResultItem> resultsList = new ArrayList<>(results.size());

        for (var item : results) {
            if (domainCountFilter.test(item)) {
                resultsList.add(item);
            }
        }

        if (!params.queryParams.domainCount().isNone()) {
            // Remove items that don't meet the domain count requirement
            // This isn't perfect because the domain count is calculated
            // after the results are sorted
            resultsList.removeIf(item -> !params.queryParams.domainCount().test(domainCountFilter.getCount(item)));
        }

        if (resultsList.size() > params.limitTotal) {
            // This can't be made a stream limit() operation because we need domainCountFilter
            // to run over the entire list to provide accurate statistics

            resultsList.subList(params.limitTotal, resultsList.size()).clear();
        }

        // populate results with the total number of results encountered from
        // the same domain so this information can be presented to the user
        for (var result : resultsList) {
            result.resultsFromDomain = domainCountFilter.getCount(result);
        }

        return resultsList;
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

        List<DecoratedSearchResultItem> decoratedItems = new ArrayList<>();
        for (var result : rawResults) {
            var docData = urlDetailsById.get(result.getDocumentId());

            if (null == docData) {
                logger.warn("No data for document id {}", result.getDocumentId());
                continue;
            }

            decoratedItems.add(createCombinedItem(result, docData, rankingContext));
        }

        if (decoratedItems.size() != rawResults.size())
            logger.warn("Result list shrunk during decoration?");

        return decoratedItems;
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
