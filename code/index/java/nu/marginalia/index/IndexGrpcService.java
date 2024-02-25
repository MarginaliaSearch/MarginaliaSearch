package nu.marginalia.index;

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
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexSearchBudget;
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
import java.util.concurrent.atomic.AtomicLong;

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

    private static final Gauge wmsa_index_query_exec_stall_time = Gauge.build()
            .name("wmsa_index_query_exec_stall_time")
            .help("Execution stall time")
            .labelNames("node")
            .register();

    private static final Gauge wmsa_index_query_exec_block_time = Gauge.build()
            .name("wmsa_index_query_exec_block_time")
            .help("Execution stall time")
            .labelNames("node")
            .register();

    private final StatefulIndex index;
    private final SearchSetsService searchSetsService;

    private final IndexQueryService indexQueryService;
    private final IndexResultValuatorService resultValuator;

    private final String nodeName;

    private final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 8);

    @Inject
    public IndexGrpcService(ServiceConfiguration serviceConfiguration,
                            StatefulIndex index,
                            SearchSetsService searchSetsService,
                            IndexQueryService indexQueryService,
                            IndexResultValuatorService resultValuator)
    {
        var nodeId = serviceConfiguration.node();
        this.nodeName = Integer.toString(nodeId);
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

            long endTime = System.currentTimeMillis() + request.getQueryLimits().getTimeoutMs();

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

            if (System.currentTimeMillis() >= endTime) {
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

    private SearchResultSet executeSearch(SearchParameters params) throws SQLException, InterruptedException {

        if (!index.isLoaded()) {
            // Short-circuit if the index is not loaded, as we trivially know that there can be no results
            return new SearchResultSet(List.of());
        }

        ResultRankingContext rankingContext = createRankingContext(params.rankingParams, params.subqueries);

        var queryExecution = new QueryExecution(rankingContext, params.fetchSize);

        var ret = queryExecution.run(params);

        wmsa_index_query_exec_block_time
                .labels(nodeName)
                .set(queryExecution.getBlockTime() / 1000.);
        wmsa_index_query_exec_stall_time
                .labels(nodeName)
                .set(queryExecution.getStallTime() / 1000.);

        return ret;
    }

    /** This class is responsible for executing a search query. It uses a thread pool to
     * execute the subqueries in parallel, and then uses another thread pool to rank the
     * results in parallel. The results are then combined into a bounded priority queue,
     * and finally the best results are returned.
     */
    private class QueryExecution {
        private static final Executor workerPool = Executors.newCachedThreadPool();

        private final ArrayBlockingQueue<CombinedDocIdList> resultCandidateQueue
                = new ArrayBlockingQueue<>(8);

        private final ResultPriorityQueue resultHeap;
        private final ResultRankingContext resultRankingContext;

        private final AtomicInteger remainingIndexTasks = new AtomicInteger(0);
        private final AtomicInteger remainingValuationTasks = new AtomicInteger(0);

        private final AtomicLong blockTime = new AtomicLong(0);
        private final AtomicLong stallTime = new AtomicLong(0);

        public long getStallTime() {
            return stallTime.get();
        }
        public long getBlockTime() {
            return blockTime.get();
        }

        private QueryExecution(ResultRankingContext resultRankingContext, int maxResults) {
            this.resultRankingContext = resultRankingContext;
            this.resultHeap = new ResultPriorityQueue(maxResults);
        }

        /** Execute a search query */
        public SearchResultSet run(SearchParameters parameters) throws SQLException, InterruptedException {

            for (var subquery : parameters.subqueries) {
                var terms = new SearchTerms(subquery);
                if (terms.isEmpty())
                    continue;

                for (var indexQuery : index.createQueries(terms, parameters.queryParams)) {
                    workerPool.execute(new IndexLookup(indexQuery, parameters.budget));
                }
            }

            for (int i = 0; i < indexValuationThreads; i++) {
                workerPool.execute(new ResultRanker(parameters, resultRankingContext));
            }

            // Wait for all tasks to complete
            awaitCompletion();

            // Return the best results
            return new SearchResultSet(
                    resultValuator.selectBestResults(parameters,
                            resultRankingContext,
                            resultHeap));
        }

        /** Wait for all tasks to complete */
        private void awaitCompletion() throws InterruptedException {
            synchronized (remainingValuationTasks) {
                while (remainingValuationTasks.get() > 0) {
                    remainingValuationTasks.wait(20);
                }
            }
        }

        /** This class is responsible for executing a subquery and adding the results to the
         * resultCandidateQueue, which depending on the state of the valuator threads may
         * or may not block*/
        class IndexLookup implements Runnable {
            private final IndexQuery query;
            private final IndexSearchBudget budget;

            IndexLookup(IndexQuery query,
                        IndexSearchBudget budget) {
                this.query = query;
                this.budget = budget;

                remainingIndexTasks.incrementAndGet();
            }

            public void run() {
                try {
                    indexQueryService.evaluateSubquery(
                            query,
                            budget,
                            this::drain
                    );
                }
                finally {
                    synchronized (remainingIndexTasks) {
                        if (remainingIndexTasks.decrementAndGet() == 0) {
                            remainingIndexTasks.notifyAll();
                        }
                    }
                }
            }

            private void drain(CombinedDocIdList resultIds) {
                long remainingTime = budget.timeLeft();

                try {
                    if (!resultCandidateQueue.offer(resultIds)) {
                        long start = System.currentTimeMillis();
                        resultCandidateQueue.offer(resultIds, remainingTime, TimeUnit.MILLISECONDS);
                        blockTime.addAndGet(System.currentTimeMillis() - start);
                    }
                }
                catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting to offer resultIds to queue", e);
                }
            }
        }

        /** This class is responsible for ranking the results and adding the best results to the
         * resultHeap, which depending on the state of the indexLookup threads may or may not block
         */
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

                        long start = System.currentTimeMillis();

                        CombinedDocIdList resultIds = resultCandidateQueue.poll(
                                Math.clamp(parameters.budget.timeLeft(), 1, 5),
                                TimeUnit.MILLISECONDS);

                        if (resultIds == null) {
                            if (remainingIndexTasks.get() == 0
                                    && resultCandidateQueue.isEmpty())
                                break;
                            else
                                continue;
                        }

                        stallTime.addAndGet(System.currentTimeMillis() - start);

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

