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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Performs an index query */
public class IndexQueryExecution {

    private static final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 16);

    private static final AtomicInteger threadCount = new AtomicInteger();
    private static final AtomicLong lookupTime = new AtomicLong();
    private static final AtomicLong valuationTime = new AtomicLong();

    private static final ExecutorService threadPool = new ThreadPoolExecutor(indexValuationThreads, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    private static final Logger log = LoggerFactory.getLogger(IndexQueryExecution.class);

    private final IndexResultRankingService rankingService;

    private final ResultRankingContext rankingContext;
    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;
    private final ResultPriorityQueue resultHeap;
    private final CountDownLatch executionCountdown;

    private final int limitTotal;
    private final int limitByDomain;

    private int evaluationJobCounter;

    static {
        Thread.ofPlatform().daemon().start(() -> {
            for (;;) {
                try {
                    TimeUnit.SECONDS.sleep(10);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
                log.info("Lookup: {}, Valuation: {}, Thread Count: {}", lookupTime.get() / 1_000_000_000.,  valuationTime.get() / 1_000_000_000., threadCount.get());
            }
        });
    }

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
        try {
            threadCount.incrementAndGet();

            final LongQueryBuffer buffer = new LongQueryBuffer(512);
            List<Future<?>> evaluationJobs = new ArrayList<>();
            try {
                while (query.hasMore() && budget.hasTimeLeft()) {

                    buffer.zero();
                    long st = System.nanoTime();
                    query.getMoreResults(buffer);
                    long et = System.nanoTime();
                    lookupTime.addAndGet(et - st);

                    if (buffer.isEmpty())
                        continue;

                    CombinedDocIdList docIds = new CombinedDocIdList(buffer);

                    boolean stealWork = false;
                    synchronized (IndexQueryExecution.this) {
                        // Hold off on spawning new evaluation jobs if we have too many queued
                        // to avoid backpressure, instead steal work into the lookup thread
                        // in this scenario

                        if (evaluationJobCounter > 2 * indexValuationThreads) {
                            stealWork = true;
                        } else {
                            evaluationJobCounter++;
                        }
                    }

                    if (stealWork) {
                        resultHeap.addAll(rankingService.rankResults(rankingContext, budget, docIds, false));
                    } else {
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
        catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
            return List.of();
        }
        finally {
            threadCount.decrementAndGet();
        }
    }

    private void evaluate(CombinedDocIdList docIds) {
        try {
            threadCount.incrementAndGet();
            if (!budget.hasTimeLeft())
                return;
            long st =  System.nanoTime();
            resultHeap.addAll(rankingService.rankResults(rankingContext, budget, docIds, false));
            long et = System.nanoTime();

            valuationTime.addAndGet(et - st);
        } catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
        } finally {
            synchronized (IndexQueryExecution.this) {
                if (--evaluationJobCounter == 0) {
                    IndexQueryExecution.this.notifyAll();
                }
            }
            threadCount.decrementAndGet();
        }
    }

    public int itemsProcessed() {
        return resultHeap.getItemsProcessed();
    }

}
