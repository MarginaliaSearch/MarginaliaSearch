package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.ResultRankingContext;
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
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class IndexGrpcService
        extends IndexApiGrpc.IndexApiImplBase
        implements DiscoverableService
{

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

    private static final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 16);

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
    public void query(RpcIndexQuery request,
                      StreamObserver<RpcDecoratedResultItem> responseObserver) {

        try {
            var params = new SearchParameters(request, getSearchSet(request));

            long endTime = System.currentTimeMillis() + request.getQueryLimits().getTimeoutMs();

            List<RpcDecoratedResultItem> results = wmsa_query_time
                    .labels(nodeName, "GRPC")
                    .time(() -> {
                        // Perform the search
                        try {
                            return executeSearch(params);
                        }
                        catch (Exception ex) {
                            logger.error("Error in handling request", ex);
                            return List.of();
                        }
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
            responseObserver.onError(Status.INTERNAL.withCause(ex).asRuntimeException());
        }
    }


    // exists for test access
    public List<RpcDecoratedResultItem> justQuery(SearchSpecification specsSet) {
        try {
            return executeSearch(new SearchParameters(specsSet, getSearchSet(specsSet)));
        }
        catch (Exception ex) {
            logger.error("Error in handling request", ex);
            return List.of();
        }
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
    public List<RpcDecoratedResultItem> executeSearch(SearchParameters params) throws Exception {

        if (!statefulIndex.isLoaded()) {
            // Short-circuit if the index is not loaded, as we trivially know that there can be no results
            return List.of();
        }

        ResultRankingContext rankingContext = ResultRankingContext.create(statefulIndex.get(), params);
        QueryExecution queryExecution = new QueryExecution(rankingContext, params.fetchSize);

        List<RpcDecoratedResultItem> ret = queryExecution.run(params);

        wmsa_index_query_exec_block_time
                .labels(nodeName)
                .set(queryExecution.getBlockTime() / 1000.);
        wmsa_index_query_exec_stall_time
                .labels(nodeName)
                .set(queryExecution.getStallTime() / 1000.);

        return ret;
    }

    /** This class is responsible for executing a search query. It uses a thread pool to
     * execute the subqueries and their valuation in parallel. The results are then combined
     * into a bounded priority queue, and finally the best results are returned.
     */
    private class QueryExecution {

        private static final Executor workerPool = Executors.newCachedThreadPool();

        /** The queue where the results from the index lookup threads are placed,
         * pending ranking by the result ranker threads */
        private final ArrayBlockingQueue<CombinedDocIdList> resultCandidateQueue
                = new ArrayBlockingQueue<>(64);
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
        public List<RpcDecoratedResultItem> run(SearchParameters parameters) throws Exception {

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
            return resultValuator.selectBestResults(parameters, resultRankingContext, resultHeap);
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
                // These queries are different indices for one subquery
                final LongQueryBuffer buffer = new LongQueryBuffer(4096);

                while (query.hasMore() && budget.hasTimeLeft())
                {
                    buffer.reset();
                    query.getMoreResults(buffer);
                    enqueueResults(new CombinedDocIdList(buffer));
                }

                buffer.dispose();
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
                catch (Exception e) {
                    logger.error("Exception while ranking results", e);
                }
                finally {
                    synchronized (remainingValuationTasks) {
                        if (remainingValuationTasks.decrementAndGet() == 0)
                            remainingValuationTasks.notifyAll();
                    }
                }
            }

            private boolean execute() throws Exception {
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
                            resultValuator.rankResults(rankingContext, resultIds, false)
                    );
                }

                return true; // keep going
            }

        }

    }

}

