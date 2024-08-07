package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.CqDataInt;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.searchset.SearchSetsService;
import nu.marginalia.index.searchset.SmallSearchSet;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.sql.SQLException;
import java.util.BitSet;
import java.util.List;
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

    private final StatefulIndex statefulIndex;
    private final SearchSetsService searchSetsService;

    private final IndexResultRankingService resultValuator;

    private final String nodeName;

    private static final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 8);

    @Inject
    public IndexGrpcService(ServiceConfiguration serviceConfiguration,
                            StatefulIndex statefulIndex,
                            SearchSetsService searchSetsService,
                            IndexResultRankingService resultValuator)
    {
        var nodeId = serviceConfiguration.node();
        this.nodeName = Integer.toString(nodeId);
        this.statefulIndex = statefulIndex;
        this.searchSetsService = searchSetsService;
        this.resultValuator = resultValuator;
    }

    // GRPC endpoint
    @SneakyThrows
    public void query(RpcIndexQuery request,
                      StreamObserver<RpcDecoratedResultItem> responseObserver) {

        try {
            var params = new SearchParameters(request, getSearchSet(request));

            long endTime = System.currentTimeMillis() + request.getQueryLimits().getTimeoutMs();

            List<RpcDecoratedResultItem> results = wmsa_query_time
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
            for (var result : results) {
                responseObserver.onNext(result);
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
    List<RpcDecoratedResultItem> justQuery(SearchSpecification specsSet) {
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

    // accessible for tests
    public List<RpcDecoratedResultItem> executeSearch(SearchParameters params) throws SQLException, InterruptedException {

        if (!statefulIndex.isLoaded()) {
            // Short-circuit if the index is not loaded, as we trivially know that there can be no results
            return List.of();
        }

        ResultRankingContext rankingContext = createRankingContext(params.rankingParams,
                params.compiledQuery,
                params.compiledQueryIds);

        var queryExecution = new QueryExecution(rankingContext, params.fetchSize);

        List<RpcDecoratedResultItem> ret = queryExecution.run(params);

        wmsa_index_query_exec_block_time
                .labels(nodeName)
                .set(queryExecution.getBlockTime() / 1000.);
        wmsa_index_query_exec_stall_time
                .labels(nodeName)
                .set(queryExecution.getStallTime() / 1000.);

        return ret;
    }

    /** This class is responsible for ranking the results and adding the best results to the
     * resultHeap, which depending on the state of the indexLookup threads may or may not block
     */
    private ResultRankingContext createRankingContext(ResultRankingParameters rankingParams,
                                                      CompiledQuery<String> compiledQuery,
                                                      CompiledQueryLong compiledQueryIds)
    {

        int[] full = new int[compiledQueryIds.size()];
        int[] prio = new int[compiledQueryIds.size()];

        BitSet ngramsMask = new BitSet(compiledQuery.size());
        BitSet regularMask = new BitSet(compiledQuery.size());

        var currentIndex = statefulIndex.get();

        for (int idx = 0; idx < compiledQueryIds.size(); idx++) {
            long id = compiledQueryIds.at(idx);
            full[idx] = currentIndex.numHits(id);
            prio[idx] = currentIndex.numHitsPrio(id);

            if (compiledQuery.at(idx).contains("_")) {
                ngramsMask.set(idx);
            }
            else {
                regularMask.set(idx);
            }
        }

        return new ResultRankingContext(currentIndex.totalDocCount(),
                rankingParams,
                ngramsMask,
                regularMask,
                new CqDataInt(full),
                new CqDataInt(prio));
    }

    /** This class is responsible for executing a search query. It uses a thread pool to
     * execute the subqueries and their valuation in parallel. The results are then combined
     * into a bounded priority queue, and finally the best results are returned.
     */
    private class QueryExecution {

        private static final Executor workerPool = Executors.newWorkStealingPool(indexValuationThreads*4);

        /** The queue where the results from the index lookup threads are placed,
         * pending ranking by the result ranker threads */
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
        public List<RpcDecoratedResultItem> run(SearchParameters parameters) throws SQLException, InterruptedException {

            var terms = new SearchTerms(parameters.query, parameters.compiledQueryIds);

            var currentIndex = statefulIndex.get();
            for (var indexQuery : currentIndex.createQueries(terms, parameters.queryParams)) {
                workerPool.execute(new IndexLookup(indexQuery, parameters.budget));
            }

            for (int i = 0; i < indexValuationThreads; i++) {
                workerPool.execute(new ResultRanker(parameters, resultRankingContext));
            }

            // Wait for all tasks to complete
            awaitCompletion();

            // Return the best results
            return resultValuator.selectBestResults(parameters, resultHeap);
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
         * or may not block */
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
                    executeSearch();
                }
                catch (Exception ex) {
                    logger.error("Error in index lookup", ex);
                }
                finally {
                    synchronized (remainingIndexTasks) {
                        if (remainingIndexTasks.decrementAndGet() == 0) {
                            remainingIndexTasks.notifyAll();
                        }
                    }
                }
            }

            private void executeSearch() {
                final LongArrayList results = new LongArrayList(512);

                // These queries are different indices for one subquery
                final LongQueryBuffer buffer = new LongQueryBuffer(512);

                while (query.hasMore() && budget.hasTimeLeft())
                {
                    buffer.reset();
                    query.getMoreResults(buffer);

                    for (int i = 0; i < buffer.end; i++) {
                        results.add(buffer.data.get(i));
                    }

                    if (results.size() >= 512) {
                        enqueueResults(new CombinedDocIdList(results));
                        results.clear();
                    }
                }

                buffer.dispose();

                if (!results.isEmpty()) {
                    enqueueResults(new CombinedDocIdList(results));
                }
            }

            private void enqueueResults(CombinedDocIdList resultIds) {
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
                    while (parameters.budget.timeLeft() > 0 && execute());
                }
                catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting to poll resultIds from queue", e);
                }
                finally {
                    synchronized (remainingValuationTasks) {
                        if (remainingValuationTasks.decrementAndGet() == 0)
                            remainingValuationTasks.notifyAll();
                    }
                }
            }
            private boolean execute() throws InterruptedException {
                long start = System.currentTimeMillis();

                // Do a relatively short poll to ensure we terminate in a timely manner
                // in the event all work is done
                final long pollTime = Math.clamp(parameters.budget.timeLeft(), 1, 5);
                CombinedDocIdList resultIds = resultCandidateQueue.poll(pollTime, TimeUnit.MILLISECONDS);

                if (resultIds == null) {
                    // check if we are done and can terminate
                    if (remainingIndexTasks.get() == 0 && resultCandidateQueue.isEmpty()) {
                        return false;
                    }
                }
                else {
                    stallTime.addAndGet(System.currentTimeMillis() - start);

                    resultHeap.addAll(
                            resultValuator.rankResults(parameters, rankingContext, resultIds)
                    );
                }

                return true; // keep going
            }

        }

    }

}

