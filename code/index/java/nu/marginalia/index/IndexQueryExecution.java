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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

/** Performs an index query */
public class IndexQueryExecution {

    private static final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 16);

    private static final ForkJoinPool lookupPool = new ForkJoinPool(indexValuationThreads);
    private static final ForkJoinPool evaluationPool = new ForkJoinPool(indexValuationThreads);

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
        for (IndexQuery query : queries) {
            lookupPool.execute(() -> lookup(query));
        }

        // Await lookup task termination (this guarantees we're no longer creating new evaluation tasks)
        executionCountdown.await();

        // Await evaluation task termination
        synchronized (IndexQueryExecution.this) {
            while (evaluationJobCounter > 0 && budget.hasTimeLeft()) {
                IndexQueryExecution.this.wait(budget.timeLeft());
            }
        }

        // Final result selection
        return rankingService.selectBestResults(limitByDomain, limitTotal, rankingContext, resultHeap.toList());
    }

    private void lookup(IndexQuery query) {
        final LongQueryBuffer buffer = new LongQueryBuffer(64);
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
                    evaluationPool.execute(() -> evaluate(docIds));
                }
            }
        } finally {
            buffer.dispose();
            executionCountdown.countDown();
        }
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
