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
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.index.client.model.results.SearchResultSet;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.index.SearchIndexSearchTerms;
import nu.marginalia.index.query.IndexQueryPriority;
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
import java.util.stream.Collectors;

@Singleton
public class IndexQueryService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // This marker is used to mark sensitive log messages that are related to queries
    // so that they can be filtered out in the production logging configuration
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private static final Counter wmsa_edge_index_query_timeouts = Counter.build().name("wmsa_edge_index_query_timeouts").help("-").register();
    private static final Gauge wmsa_edge_index_query_cost = Gauge.build().name("wmsa_edge_index_query_cost").help("-").register();
    private static final Histogram wmsa_edge_index_query_time = Histogram.build().name("wmsa_edge_index_query_time").linearBuckets(25/1000., 25/1000., 15).help("-").register();

    private final IndexQueryExecutor queryExecutor;
    private final Gson gson = GsonFactory.get();

    private final SearchIndex index;
    private final IndexSearchSetsService searchSetsService;

    private final IndexMetadataService metadataService;
    private final SearchTermsService searchTermsSvc;


    @Inject
    public IndexQueryService(IndexQueryExecutor queryExecutor,
                             SearchIndex index,
                             IndexSearchSetsService searchSetsService,
                             IndexMetadataService metadataService,
                             SearchTermsService searchTerms) {
        this.queryExecutor = queryExecutor;
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

        var rankingContext = createRankingContext(params.rankingParams, params.subqueries);

        logger.info(queryMarker, "{}", params.queryParams);

        var resultIds = evaluateSubqueries(params);
        var resultItems = calculateResultScores(params, rankingContext, resultIds);

        logger.info(queryMarker, "After filtering: {} -> {}", resultIds.size(), resultItems.size());

        var bestResults = selectBestResults(params, resultItems);

        return new SearchResultSet(bestResults, rankingContext);
    }

    /* This is used in result ranking, and is also routed back up the search service in order to recalculate BM-25
     * accurately */
    private ResultRankingContext createRankingContext(ResultRankingParameters rankingParams, List<SearchSubquery> subqueries) {
        final var termToId = searchTermsSvc.getAllIncludeTerms(subqueries);
        final Map<String, Integer> termFrequencies = new HashMap<>(termToId.size());
        final Map<String, Integer> prioFrequencies = new HashMap<>(termToId.size());

        termToId.forEach((key, id) -> termFrequencies.put(key, index.getTermFrequency(id)));
        termToId.forEach((key, id) -> prioFrequencies.put(key, index.getTermFrequencyPrio(id)));

        return new ResultRankingContext(index.getTotalDocCount(),
                rankingParams,
                termFrequencies,
                prioFrequencies);
    }

    /** Execute subqueries and return a list of document ids.  The index is queried for each subquery,
     * at different priorty depths until timeout is reached or the results are all visited.
     * <br>
     * Then the results are combined.
     * */
    private final ThreadLocal<TLongArrayList> resultsArrayListPool = ThreadLocal.withInitial(TLongArrayList::new);
    private TLongList evaluateSubqueries(SearchParameters params) {
        final TLongArrayList results = resultsArrayListPool.get();
        results.resetQuick();
        results.ensureCapacity(params.fetchSize);

        // These queries are various term combinations
        for (var subquery : params.subqueries) {

            if (!params.hasTimeLeft()) {
                logger.info("Query timed out {}, ({}), -{}",
                        subquery.searchTermsInclude, subquery.searchTermsAdvice, subquery.searchTermsExclude);
                break;
            }

            logger.info(queryMarker, "{}", subquery);

            final SearchIndexSearchTerms searchTerms = searchTermsSvc.getSearchTerms(subquery);

            if (searchTerms.isEmpty()) {
                logger.info(queryMarker, "empty");
                continue;
            }

            logSearchTerms(subquery, searchTerms);

            // These queries are different indices for one subquery
            List<IndexQuery> queries = params.createIndexQueries(index, searchTerms);
            for (var query : queries) {

                if (!params.hasTimeLeft())
                    break;

                if (shouldOmitQuery(params, query, results.size())) {
                    logger.info(queryMarker, "Omitting {}", query);
                    continue;
                }

                int cnt = queryExecutor.executeQuery(query, results, params);

                logger.info(queryMarker, "{} from {}", cnt, query);
            }
        }

        return results;
    }

    /** @see IndexQueryPriority */
    private boolean shouldOmitQuery(SearchParameters params, IndexQuery query, int resultCount) {

        var priority = query.queryPriority;

        return switch (priority) {
            case BEST -> false;
            case GOOD -> resultCount > params.fetchSize / 4;
            case FALLBACK -> resultCount > params.fetchSize / 8;
        };
    }

    private void logSearchTerms(SearchSubquery subquery, SearchIndexSearchTerms searchTerms) {

        // This logging should only be enabled in testing, as it is very verbose
        // and contains sensitive information

        if (!logger.isInfoEnabled(queryMarker)) {
            return;
        }

        var includes = subquery.searchTermsInclude;
        var advice = subquery.searchTermsAdvice;
        var excludes = subquery.searchTermsExclude;
        var priority = subquery.searchTermsPriority;

        for (int i = 0; i < includes.size(); i++) {
            logger.info(queryMarker, "{} -> {} I", includes.get(i),
                    Long.toHexString(searchTerms.includes().getLong(i))
            );
        }
        for (int i = 0; i < advice.size(); i++) {
            logger.info(queryMarker, "{} -> {} A", advice.get(i),
                    Long.toHexString(searchTerms.includes().getLong(includes.size() + i))
            );
        }
        for (int i = 0; i < excludes.size(); i++) {
            logger.info(queryMarker, "{} -> {} E", excludes.get(i),
                    Long.toHexString(searchTerms.excludes().getLong(i))
            );
        }
        for (int i = 0; i < priority.size(); i++) {
            logger.info(queryMarker, "{} -> {} P", priority.get(i),
                    Long.toHexString(searchTerms.priority().getLong(i))
            );
        }
    }

    private List<SearchResultItem> calculateResultScores(SearchParameters params, ResultRankingContext rankingContext, TLongList resultIds) {

        final var evaluator = new IndexResultValuator(metadataService,
                resultIds,
                rankingContext,
                params.subqueries,
                params.queryParams);

        // Sort the ids for more favorable access patterns on disk
        resultIds.sort();

        // Parallel stream to calculate scores is a minor performance boost
        return Arrays.stream(resultIds.toArray())
                .parallel()
                .mapToObj(evaluator::calculatePreliminaryScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<SearchResultItem> selectBestResults(SearchParameters params, List<SearchResultItem> results) {

        var domainCountFilter = new IndexResultDomainDeduplicator(params.limitByDomain);

        results.sort(Comparator.naturalOrder());

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

