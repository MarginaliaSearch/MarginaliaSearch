package nu.marginalia.index.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultRankingContext;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import nu.marginalia.index.results.IndexMetadataService;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.results.IndexResultValuator;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.results.IndexResultDomainDeduplicator;
import nu.marginalia.index.svc.searchset.SmallSearchSet;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.*;

@Singleton
public class IndexQueryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private static final Counter wmsa_edge_index_query_timeouts = Counter.build().name("wmsa_edge_index_query_timeouts").help("-").register();
    private static final Gauge wmsa_edge_index_query_cost = Gauge.build().name("wmsa_edge_index_query_cost").help("-").register();
    private static final Histogram wmsa_edge_index_query_time = Histogram.build().name("wmsa_edge_index_query_time").linearBuckets(25/1000., 25/1000., 15).help("-").register();

    private final Gson gson = GsonFactory.get();

    private final SearchIndex index;
    private final IndexSearchSetsService searchSetsService;

    private final IndexMetadataService metadataService;
    private final SearchTermsService searchTermsSvc;


    @Inject
    public IndexQueryService(SearchIndex index,
                             IndexSearchSetsService searchSetsService,
                             IndexMetadataService metadataService,
                             SearchTermsService searchTerms) {
        this.index = index;
        this.searchSetsService = searchSetsService;
        this.metadataService = metadataService;
        this.searchTermsSvc = searchTerms;
    }

    public Object search(Request request, Response response) {
        String json = request.body();
        SearchSpecification specsSet = gson.fromJson(json, SearchSpecification.class);

        if (!index.isAvailable()) {
            Spark.halt(503, "Index is not loaded");
        }

        try {
            return wmsa_edge_index_query_time.time(() -> {
                var params = new SearchParameters(specsSet, getSearchSet(specsSet));

                SearchResultSet results = executeSearch(params);

                logger.info(queryMarker, "Index Result Count: {}", results.size());

                wmsa_edge_index_query_cost.set(params.getDataCost());
                if (!params.hasTimeLeft()) {
                    wmsa_edge_index_query_timeouts.inc();
                }

                return results;
            });
        }
        catch (HaltException ex) {
            logger.warn("Halt", ex);
            throw ex;
        }
        catch (Exception ex) {
            logger.info("Error during search {}({}) (query: {})", ex.getClass().getSimpleName(), ex.getMessage(), json);
            logger.info("Error", ex);
            Spark.halt(500, "Error");
            return null;
        }
    }

    // exists for test access
    SearchResultSet justQuery(SearchSpecification specsSet) {
        return executeSearch(new SearchParameters(specsSet, getSearchSet(specsSet)));
    }

    private SearchSet getSearchSet(SearchSpecification specsSet) {

        if (specsSet.domains != null && !specsSet.domains.isEmpty()) {
            return new SmallSearchSet(specsSet.domains);
        }

        return searchSetsService.getSearchSetByName(specsSet.searchSetIdentifier);
    }

    private SearchResultSet executeSearch(SearchParameters params) {

        var resultIds = evaluateSubqueries(params);
        var resultItems = calculateResultScores(params, resultIds);
        var bestResults = selectBestResults(params, resultItems);

        return new SearchResultSet(bestResults, createRankingContext(params.subqueries));
    }

    /* This information is routed back up the search service in order to calculate BM-25
     * accurately  */
    private SearchResultRankingContext createRankingContext(List<SearchSubquery> subqueries) {
        final var termToId = searchTermsSvc.getAllIncludeTerms(subqueries);
        final var termFrequencies = new HashMap<>(termToId);

        termToId.forEach((key, id) -> termFrequencies.put(key, index.getTermFrequency(id)));

        return new SearchResultRankingContext(index.getTotalDocCount(), termFrequencies);
    }

    private TLongList evaluateSubqueries(SearchParameters params) {
        final TLongList results = new TLongArrayList(params.fetchSize);

        logger.info(queryMarker, "{}", params.queryParams);

        outer:
        // These queries are various term combinations
        for (var subquery : params.subqueries) {
            final SearchIndexSearchTerms searchTerms = searchTermsSvc.getSearchTerms(subquery);

            if (searchTerms.isEmpty()) {
                continue;
            }

            // These queries are different indices for one subquery
            List<IndexQuery> queries = params.createIndexQueries(index, searchTerms);
            for (var query : queries) {
                var resultsForSq = executeQuery(query, params);
                logger.info(queryMarker, "{} from {}", resultsForSq.size(), subquery);
                results.addAll(resultsForSq);

                if (!params.hasTimeLeft()) {
                    logger.info("Query timed out {}, ({}), -{}",
                            subquery.searchTermsInclude, subquery.searchTermsAdvice, subquery.searchTermsExclude);
                    break outer;
                }
            }
        }

        return results;
    }

    private TLongArrayList executeQuery(IndexQuery query, SearchParameters params)
    {
        final TLongArrayList results = new TLongArrayList(params.fetchSize);
        final LongQueryBuffer buffer = new LongQueryBuffer(params.fetchSize);

        while (query.hasMore()
                && results.size() < params.fetchSize
                && params.budget.hasTimeLeft())
        {
            buffer.reset();
            query.getMoreResults(buffer);

            for (int i = 0; i < buffer.size() && results.size() < params.fetchSize; i++) {
                results.add(buffer.data[i]);
            }
        }

        params.dataCost += query.dataCost();

        return results;
    }

    private ArrayList<SearchResultItem> calculateResultScores(SearchParameters params, TLongList resultIds) {

        final var evaluator = new IndexResultValuator(metadataService, resultIds, params.subqueries, params.queryParams);

        ArrayList<SearchResultItem> items = new ArrayList<>(resultIds.size());

        // Note, this is a pre-sorting the result IDs. This is a performance optimization, as it will cluster
        // disk access to adjacent parts of the forward index when fetching metadata
        //
        // This is *not* where the actual search results are sorted
        resultIds.sort();

        resultIds.forEach(id -> {
            var item = evaluator.calculatePreliminaryScore(id);

            if (!item.getScore().isEmpty()) {
                items.add(item);
            }

            return true;
        });

        logger.info(queryMarker, "After filtering: {} -> {}", resultIds.size(), items.size());


        return items;
    }

    private List<SearchResultItem> selectBestResults(SearchParameters params, List<SearchResultItem> results) {

        var domainCountFilter = new IndexResultDomainDeduplicator(params.limitByDomain);

        results.sort(Comparator.comparing(SearchResultItem::getScore).reversed()
                .thenComparingInt(SearchResultItem::getRanking)
                .thenComparingInt(SearchResultItem::getUrlIdInt));

        List<SearchResultItem> resultsList = new ArrayList<>(results.size());

        for (var item : results) {
            if (domainCountFilter.test(item)) {
                resultsList.add(item);
            }
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

}

