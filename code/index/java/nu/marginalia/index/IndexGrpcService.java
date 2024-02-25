package nu.marginalia.index;

import com.google.common.collect.MinMaxPriorityQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.api.searchquery.model.results.*;
import nu.marginalia.index.index.IndexQueryService;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.index.results.IndexResultValuatorService;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.index.searchset.SmallSearchSet;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class IndexGrpcService extends IndexApiGrpc.IndexApiImplBase {

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


    private final StatefulIndex index;
    private final SearchSetsService searchSetsService;

    private final IndexQueryService indexQueryService;
    private final IndexResultValuatorService resultValuator;

    private final int nodeId;


    @Inject
    public IndexGrpcService(ServiceConfiguration serviceConfiguration,
                            StatefulIndex index,
                            SearchSetsService searchSetsService,
                            IndexQueryService indexQueryService,
                            IndexResultValuatorService resultValuator)
    {
        this.nodeId = serviceConfiguration.node();
        this.index = index;
        this.searchSetsService = searchSetsService;
        this.resultValuator = resultValuator;
        this.indexQueryService = indexQueryService;
    }

    // GRPC endpoint
    @SneakyThrows
    public void query(RpcIndexQuery request,
                      StreamObserver<RpcDecoratedResultItem> responseObserver) {

        try {
            var params = new SearchParameters(request, getSearchSet(request));
            final String nodeName = Integer.toString(nodeId);

            SearchResultSet results = wmsa_query_time
                    .labels(nodeName, "GRPC")
                    .time(() -> {
                        // Perform the search
                        return executeSearch(params);
                    });

            // Prometheus bookkeeping
            wmsa_query_cost
                    .labels(nodeName, "GRPC")
                    .set(params.getDataCost());

            if (!params.hasTimeLeft()) {
                wmsa_query_timeouts
                        .labels(nodeName, "GRPC")
                        .inc();
            }

            // Send the results back to the client
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

        ResultRankingContext rankingContext = createRankingContext(params.rankingParams, params.subqueries);

        logger.info(queryMarker, "{}", params.queryParams);

        return new QueryExecution(rankingContext, params.fetchSize)
                .run(params);
    }

    private class QueryExecution {
        private static final Executor queryExecutor = Executors.newCachedThreadPool();
        private static final Executor rankingExecutor = Executors.newCachedThreadPool();
        private final ArrayBlockingQueue<CombinedDocIdList> resultQueue = new ArrayBlockingQueue<>(8);
        private final ResultPriorityQueue resultHeap;
        private final ResultRankingContext resultRankingContext;

        private final AtomicInteger remainingIndexTasks = new AtomicInteger(0);
        private final AtomicInteger remainingValuationTasks = new AtomicInteger(0);

        private QueryExecution(ResultRankingContext resultRankingContext, int maxResults) {
            this.resultRankingContext = resultRankingContext;
            this.resultHeap = new ResultPriorityQueue(maxResults);
        }

        public SearchResultSet run(SearchParameters parameters) throws SQLException {
            for (var subquery : parameters.subqueries) {
                queryExecutor.execute(new IndexLookup(subquery, parameters));
            }

            for (int i = 0; i < 16; i++) {
                rankingExecutor.execute(new ResultRanker(parameters, resultRankingContext));
            }

            // Wait for all tasks to complete
            synchronized (remainingValuationTasks) {
                while (remainingValuationTasks.get() > 0) {
                    try {
                        remainingValuationTasks.wait(20);
                    }
                    catch (InterruptedException e) {
                        logger.warn("Interrupted while waiting for tasks to complete", e);
                    }
                }
            }

            return new SearchResultSet(resultValuator.selectBestResults(parameters, resultRankingContext, resultHeap));
        }

        class IndexLookup implements Runnable {
            private final SearchSubquery subquery;
            private final SearchParameters parameters;

            IndexLookup(SearchSubquery subquery, SearchParameters parameters) {
                this.subquery = subquery;
                this.parameters = parameters;

                logger.info("Starting index task");

                remainingIndexTasks.incrementAndGet();
            }

            public void run() {
                try {
                    indexQueryService.evaluateSubquery(
                            subquery,
                            parameters.queryParams,
                            parameters.budget,
                            parameters.fetchSize,
                            this::drain
                    );
                }
                finally {
                    synchronized (remainingIndexTasks) {
                        if (remainingIndexTasks.decrementAndGet() == 0) {
                            remainingIndexTasks.notifyAll();
                        }
                    }
                    logger.info("Terminating index task");
                }
            }

            private void drain(CombinedDocIdList resultIds) {
                long remainingTime = parameters.budget.timeLeft();

                try {
                    resultQueue.offer(resultIds, remainingTime, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting to offer resultIds to queue", e);
                }
            }
        }

        class ResultRanker implements Runnable {
            private final SearchParameters parameters;
            private final ResultRankingContext rankingContext;

            ResultRanker(SearchParameters parameters, ResultRankingContext rankingContext) {
                this.parameters = parameters;
                this.rankingContext = rankingContext;

                remainingValuationTasks.incrementAndGet();
            }

            public void run() {
                try {
                    while (parameters.budget.timeLeft() > 0) {

                        CombinedDocIdList resultIds = resultQueue.poll(
                                Math.clamp(parameters.budget.timeLeft(), 1, 25),
                                TimeUnit.MILLISECONDS);
                        if (resultIds == null) {
                            if (remainingIndexTasks.get() == 0 && resultQueue.isEmpty())
                                break;
                            else
                                continue;
                        }

                        var bestResults = resultValuator.rankResults(parameters, rankingContext, resultIds);

                        resultHeap.addAll(bestResults);
                    }
                }
                catch (Exception e) {
                    logger.warn("Interrupted while waiting to poll resultIds from queue", e);
                }
                finally {
                    synchronized (remainingValuationTasks) {
                        if (remainingValuationTasks.decrementAndGet() == 0)
                            remainingValuationTasks.notifyAll();
                    }
                }
            }
        }

    }

    private ResultRankingContext createRankingContext(ResultRankingParameters rankingParams, List<SearchSubquery> subqueries) {
        final var termToId = SearchTermsUtil.getAllIncludeTerms(subqueries);
        final Map<String, Integer> termFrequencies = new HashMap<>(termToId.size());
        final Map<String, Integer> prioFrequencies = new HashMap<>(termToId.size());

        termToId.forEach((key, id) -> termFrequencies.put(key, index.getTermFrequency(id)));
        termToId.forEach((key, id) -> prioFrequencies.put(key, index.getTermFrequencyPrio(id)));

        return new ResultRankingContext(index.getTotalDocCount(),
                rankingParams,
                termFrequencies,
                prioFrequencies);
    }

}

