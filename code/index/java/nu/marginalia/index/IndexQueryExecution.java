package nu.marginalia.index;

import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.model.ResultRankingContext;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Performs an index query */
public class IndexQueryExecution {

    private static final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 16);

    private static final ExecutorService threadPool = new ThreadPoolExecutor(indexValuationThreads, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    private final IndexResultRankingService rankingService;

    private final ResultRankingContext rankingContext;
    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;
    private final ResultPriorityQueue resultHeap;
    private final CountDownLatch executionCountdown;

    private final int limitTotal;
    private final int limitByDomain;

    private int evaluationJobCounter;

    public IndexQueryExecution(SearchParameters params,
                               IndexResultRankingService rankingService,
                               CombinedIndexReader currentIndex) {
        this.rankingService = rankingService;

        resultHeap = new ResultPriorityQueue(params.fetchSize);

        budget = params.budget;
        limitByDomain = params.limitByDomain;
        limitTotal = params.limitTotal;

        rankingContext = ResultRankingContext.create(currentIndex, params);
        queries = currentIndex.createQueries(new SearchTerms(params.query, params.compiledQueryIds), params.queryParams);
        executionCountdown = new CountDownLatch(queries.size());

        evaluationJobCounter = 0;
    }

    public List<RpcDecoratedResultItem> run() throws InterruptedException, SQLException {
        // Spawn lookup tasks for each query
        List<Future<?>> tasks = new ArrayList<>();
        for (IndexQuery query : queries) {
            threadPool.submit(() -> {
                var lookupJobs = lookup(query);
                synchronized (tasks) {
                    tasks.addAll(lookupJobs);
                }
            });
        }

        // Await lookup task termination (this guarantees we're no longer creating new evaluation tasks)
        executionCountdown.await();

        // Await evaluation task termination
        synchronized (IndexQueryExecution.this) {
            while (evaluationJobCounter > 0 && budget.hasTimeLeft()) {
                IndexQueryExecution.this.wait(budget.timeLeft());
            }
        }

        synchronized (tasks) {
            for (var job : tasks) {
                job.cancel(true);
            }
        }

        // Final result selection
        return rankingService.selectBestResults(limitByDomain, limitTotal, rankingContext, resultHeap.toList());
    }

    private List<Future<?>> lookup(IndexQuery query) {
        final LongQueryBuffer buffer = new LongQueryBuffer(1024);
        List<Future<?>> evaluationJobs = new ArrayList<>();
        try {
            while (query.hasMore() && budget.hasTimeLeft()) {

                buffer.zero();
                query.getMoreResults(buffer);

                if (buffer.isEmpty())
                    continue;

                CombinedDocIdList docIds = new CombinedDocIdList(buffer);

                boolean stealWork = false;
                synchronized (IndexQueryExecution.this) {
                    // Hold off on spawning new evaluation jobs if we have too many queued
                    // to avoid backpressure, instead steal work into the lookup thread
                    // in this scenario

                    if (evaluationJobCounter > indexValuationThreads * 8) {
                        stealWork = true;
                    }
                    else {
                        evaluationJobCounter++;
                    }
                }

                if (stealWork) {
                    resultHeap.addAll(rankingService.rankResults(rankingContext, budget, docIds, false));
                }
                else {
                    // Spawn an evaluation task
                    evaluationJobs.add(threadPool.submit(() -> evaluate(docIds)));
                }
            }
        } catch (RuntimeException ex) {
            evaluationJobs.forEach(future -> future.cancel(true));
        } finally {
            buffer.dispose();
            executionCountdown.countDown();
        }

        return evaluationJobs;
    }

    private void evaluate(CombinedDocIdList docIds) {
        try {
            if (!budget.hasTimeLeft())
                return;
            resultHeap.addAll(rankingService.rankResults(rankingContext, budget, docIds, false));
        } finally {
            synchronized (IndexQueryExecution.this) {
                if (--evaluationJobCounter == 0) {
                    IndexQueryExecution.this.notifyAll();
                }
            }
        }
    }

    public int itemsProcessed() {
        return resultHeap.getItemsProcessed();
    }

}
