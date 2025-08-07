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
    private final CountDownLatch lookupCountdown;
    private final CountDownLatch evaluationCountdown;

    private final ArrayBlockingQueue<CombinedDocIdList> evaluationQueue = new ArrayBlockingQueue<>(8);

    private final int limitTotal;
    private final int limitByDomain;

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

        lookupCountdown = new CountDownLatch(queries.size());
        evaluationCountdown = new CountDownLatch(indexValuationThreads);
    }

    public List<RpcDecoratedResultItem> run() throws InterruptedException, SQLException {
        // Spawn lookup tasks for each query
        for (int i = 0; i < indexValuationThreads; i++) {
            threadPool.submit(this::evaluate);
        }
        for (IndexQuery query : queries) {
            threadPool.submit(() -> lookup(query));
        }

        // Await lookup task termination
        lookupCountdown.await();
        evaluationCountdown.await();

        // Final result selection
        return rankingService.selectBestResults(limitByDomain, limitTotal, rankingContext, resultHeap.toList());
    }

    private List<Future<?>> lookup(IndexQuery query) {
        final LongQueryBuffer buffer = new LongQueryBuffer(4096);
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
                evaluationQueue.offer(docIds, Math.max(1, budget.timeLeft()), TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException | InterruptedException ex) {
            log.error("Exception in lookup thread", ex);
        } finally {
            buffer.dispose();
            lookupCountdown.countDown();
        }

        return evaluationJobs;
    }

    private void evaluate() {
        try {
            while (budget.hasTimeLeft() && (lookupCountdown.getCount() > 0 || !evaluationQueue.isEmpty())) {
                var rankingItems = evaluationQueue.poll(Math.clamp(budget.timeLeft(), 1, 5), TimeUnit.MILLISECONDS);
                if (rankingItems == null) continue;

                long st =  System.nanoTime();
                resultHeap.addAll(rankingService.rankResults(rankingContext, budget, rankingItems, false));
                long et = System.nanoTime();
                valuationTime.addAndGet(et - st);
            }
        } catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
        } finally {
            evaluationCountdown.countDown();
        }
    }

    public int itemsProcessed() {
        return resultHeap.getItemsProcessed();
    }

}
