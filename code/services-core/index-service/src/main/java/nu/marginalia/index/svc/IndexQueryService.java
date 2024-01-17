package nu.marginalia.index.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.index.api.*;
import nu.marginalia.index.api.IndexApiGrpc.IndexApiImplBase;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
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
import nu.marginalia.index.results.IndexResultDecorator;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.results.IndexResultValuator;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.results.IndexResultDomainDeduplicator;
import nu.marginalia.index.svc.searchset.SmallSearchSet;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class IndexQueryService extends IndexApiImplBase {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // This marker is used to mark sensitive log messages that are related to queries
    // so that they can be filtered out in the production logging configuration
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private static final Counter wmsa_query_timeouts = Counter.build()
            .name("wmsa_index_query_timeouts")
            .help("Query timeout counter")
            .labelNames("node", "api")
            .register();
    private static final Gauge wmsa_query_cost = Gauge.build()
            .name("wmsa_index_query_cost")
            .help("Computational cost of query")
            .labelNames("node", "api")
            .register();
    private static final Histogram wmsa_query_time = Histogram.build()
            .name("wmsa_index_query_time")
            .linearBuckets(0.05, 0.05, 15)
            .labelNames("node", "api")
            .help("Index-side query time")
            .register();

    private final IndexQueryExecutor queryExecutor;
    private final Gson gson = GsonFactory.get();

    private final SearchIndex index;
    private final IndexResultDecorator resultDecorator;
    private final IndexSearchSetsService searchSetsService;

    private final IndexMetadataService metadataService;
    private final SearchTermsService searchTermsSvc;
    private final int nodeId;


    @Inject
    public IndexQueryService(IndexQueryExecutor queryExecutor,
                             ServiceConfiguration serviceConfiguration,
                             SearchIndex index,
                             IndexResultDecorator resultDecorator,
                             IndexSearchSetsService searchSetsService,
                             IndexMetadataService metadataService,
                             SearchTermsService searchTerms)
    {
        this.nodeId = serviceConfiguration.node();
        this.queryExecutor = queryExecutor;
        this.index = index;
        this.resultDecorator = resultDecorator;
        this.searchSetsService = searchSetsService;
        this.metadataService = metadataService;
        this.searchTermsSvc = searchTerms;
    }

    public DocumentMetadata debugEndpointDocMetadata(Request request, Response response) {
        String docId =  request.queryParams("docId");
        response.type("application/json");

        return new DocumentMetadata(index.getDocumentMetadata(Long.parseLong(docId)));
    }

    public WordMetadata debugEndpointWordMetadata(Request request, Response response) {
        String word =  request.queryParams("word");
        String docId =  request.queryParams("docId");
        response.type("application/json");

        return new WordMetadata(index.getTermMetadata(
                searchTermsSvc.getWordId(word),
                new long[] { Long.parseLong(docId) }
        )[0]);
    }

    public String debugEndpointWordEncoding(Request request, Response response) {
        String word =  request.queryParams("word");
        response.type("application/json");

        return Long.toHexString(searchTermsSvc.getWordId(word));
    }

    public Object search(Request request, Response response) {
        final String json = request.body();
        final SearchSpecification specsSet = gson.fromJson(json, SearchSpecification.class);

        if (!index.isAvailable()) {
            Spark.halt(503, "Index is not loaded");
        }

        final String nodeName = Integer.toString(nodeId);

        try {
            return wmsa_query_time
                    .labels(nodeName, "REST")
                    .time(() -> {
                var params = new SearchParameters(specsSet, getSearchSet(specsSet));

                SearchResultSet results = executeSearch(params);

                logger.info(queryMarker, "Index Result Count: {}", results.size());

                wmsa_query_cost
                        .labels(nodeName, "REST")
                        .set(params.getDataCost());

                if (!params.hasTimeLeft()) {
                    wmsa_query_timeouts
                            .labels(nodeName, "REST")
                            .inc();
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


    // GRPC endpoint
    @SneakyThrows
    public void query(nu.marginalia.index.api.RpcIndexQuery request,
                      io.grpc.stub.StreamObserver<nu.marginalia.index.api.RpcDecoratedResultItem> responseObserver) {

        try {
            var params = new SearchParameters(request, getSearchSet(request));

            final String nodeName = Integer.toString(nodeId);

            SearchResultSet results = wmsa_query_time
                    .labels(nodeName, "GRPC")
                    .time(() -> executeSearch(params));

            wmsa_query_cost
                    .labels(nodeName, "GRPC")
                    .set(params.getDataCost());

            if (!params.hasTimeLeft()) {
                wmsa_query_timeouts
                        .labels(nodeName, "GRPC")
                        .inc();
            }

            for (var result : results.results) {

                var rawResult = result.rawIndexResult;

                var rawItem = RpcRawResultItem.newBuilder();
                rawItem.setCombinedId(rawResult.combinedId);
                rawItem.setResultsFromDomain(rawResult.resultsFromDomain);

                for (var score : rawResult.keywordScores) {
                    rawItem.addKeywordScores(
                            RpcResultKeywordScore.newBuilder()
                                    .setEncodedDocMetadata(score.encodedDocMetadata())
                                    .setEncodedWordMetadata(score.encodedWordMetadata())
                                    .setKeyword(score.keyword)
                                    .setHtmlFeatures(score.htmlFeatures())
                                    .setHasPriorityTerms(score.hasPriorityTerms())
                                    .setSubquery(score.subquery)
                    );
                }

                var decoratedBuilder = RpcDecoratedResultItem.newBuilder()
                        .setDataHash(result.dataHash)
                        .setDescription(result.description)
                        .setFeatures(result.features)
                        .setFormat(result.format)
                        .setRankingScore(result.rankingScore)
                        .setTitle(result.title)
                        .setUrl(result.url.toString())
                        .setWordsTotal(result.wordsTotal)
                        .setRawItem(rawItem);

                if (result.pubYear != null) {
                    decoratedBuilder.setPubYear(result.pubYear);
                }
                responseObserver.onNext(decoratedBuilder.build());
            }

            responseObserver.onCompleted();
        }
        catch (Exception ex) {
            logger.error("Error in handling request", ex);
            responseObserver.onError(ex);
        }
    }

    // exists for test access
    @SneakyThrows
    SearchResultSet justQuery(SearchSpecification specsSet) {
        return executeSearch(new SearchParameters(specsSet, getSearchSet(specsSet)));
    }

    private SearchSet getSearchSet(SearchSpecification specsSet) {

        if (specsSet.domains != null && !specsSet.domains.isEmpty()) {
            return new SmallSearchSet(specsSet.domains);
        }

        return searchSetsService.getSearchSetByName(specsSet.searchSetIdentifier);
    }
    private SearchSet getSearchSet(RpcIndexQuery request) {

        if (request.getDomainsCount() > 0) {
            return new SmallSearchSet(request.getDomainsList());
        }

        return searchSetsService.getSearchSetByName(request.getSearchSetIdentifier());
    }
    private SearchResultSet executeSearch(SearchParameters params) throws SQLException {

        if (!index.isLoaded()) {
            // Short-circuit if the index is not loaded, as we trivially know that there can be no results
            return new SearchResultSet(List.of());
        }

        var rankingContext = createRankingContext(params.rankingParams, params.subqueries);

        logger.info(queryMarker, "{}", params.queryParams);

        var resultIds = evaluateSubqueries(params);
        var resultItems = calculateResultScores(params, rankingContext, resultIds);

        logger.info(queryMarker, "After filtering: {} -> {}", resultIds.size(), resultItems.size());

        var bestResults = selectBestResults(params, resultItems);

        return new SearchResultSet(resultDecorator.decorateAndRerank(bestResults, rankingContext));
    }

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

}

