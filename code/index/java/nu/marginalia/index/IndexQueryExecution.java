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
import nu.marginalia.skiplist.SkipListConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Performs an index query */
public class IndexQueryExecution {

    private static final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 16);
    private static final int indexPreparationThreads = Integer.getInteger("index.preparationThreads", 4);

    // Since most NVMe drives have a maximum read size of 128 KB, and most small reads are 512B
    // this should probably be 128*1024 / 512 = 256 to reduce queue depth and optimize tail latency
    private static final int evaluationBatchSize = 256;

    // This should probably be SkipListConstants.BLOCK_SIZE / 16 in order to reduce the number of unnecessary read
    // operations per lookup and again optimize tail latency
    private static final int lookupBatchSize = SkipListConstants.BLOCK_SIZE / 16;

    private static final AtomicLong lookupTime = new AtomicLong();
    private static final AtomicLong prepTime = new AtomicLong();
    private static final AtomicLong valuationTime = new AtomicLong();

    private static final ExecutorService threadPool = new ThreadPoolExecutor(indexValuationThreads, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    private static final Logger log = LoggerFactory.getLogger(IndexQueryExecution.class);

    private final IndexResultRankingService rankingService;

    private final ResultRankingContext rankingContext;
    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;
    private final ResultPriorityQueue resultHeap;
    private final CountDownLatch lookupCountdown;
    private final CountDownLatch preparationCountdown;
    private final CountDownLatch rankingCountdown;

    private final ArrayBlockingQueue<CombinedDocIdList> fullPreparationQueue = new ArrayBlockingQueue<>(8, true);
    private final ArrayBlockingQueue<CombinedDocIdList> priorityPreparationQueue = new ArrayBlockingQueue<>(8, true);
    private final ArrayBlockingQueue<IndexResultRankingService.RankingData> fullEvaluationQueue = new ArrayBlockingQueue<>(8, true);
    private final ArrayBlockingQueue<IndexResultRankingService.RankingData> priorityEvaluationQueue = new ArrayBlockingQueue<>(8, true);

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
                log.info("Lookup: {}, Valuation: {}, Prep Time: {}", lookupTime.get() / 1_000_000_000.,  valuationTime.get() / 1_000_000_000., prepTime.get() / 1_000_000_000.);
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
        queries = currentIndex.createQueries(new SearchTerms(params.query, params.compiledQueryIds), params.queryParams, budget);

        lookupCountdown = new CountDownLatch(queries.size());
        preparationCountdown = new CountDownLatch(indexPreparationThreads * 2);
        rankingCountdown = new CountDownLatch(indexValuationThreads * 2);
    }

    public List<RpcDecoratedResultItem> run() throws InterruptedException, SQLException {
        for (IndexQuery query : queries) {
            threadPool.submit(() -> lookup(query));
        }

        for (int i = 0; i < indexPreparationThreads; i++) {
            threadPool.submit(() -> prepare(priorityPreparationQueue, priorityEvaluationQueue));
            threadPool.submit(() -> prepare(fullPreparationQueue, fullEvaluationQueue));
        }

        // Spawn lookup tasks for each query
        for (int i = 0; i < indexValuationThreads; i++) {
            threadPool.submit(() -> evaluate(priorityEvaluationQueue));
            threadPool.submit(() -> evaluate(fullEvaluationQueue));
        }

        // Await lookup task termination
        lookupCountdown.await();
        preparationCountdown.await();
        rankingCountdown.await();

        // Deallocate any leftover ranking data buffers
        for (var data : priorityEvaluationQueue) {
            data.close();
        }
        for (var data : fullEvaluationQueue) {
            data.close();
        }

        // Final result selection
        return rankingService.selectBestResults(limitByDomain, limitTotal, rankingContext, resultHeap.toList());
    }

    private List<Future<?>> lookup(IndexQuery query) {
        final LongQueryBuffer buffer = new LongQueryBuffer(lookupBatchSize);
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

                var queue = query.isPrioritized() ? priorityPreparationQueue : fullPreparationQueue;

                if (buffer.end <= evaluationBatchSize) {
                    var docIds = new CombinedDocIdList(buffer);

                    if (!queue.offer(docIds, Math.max(1, budget.timeLeft()), TimeUnit.MILLISECONDS))
                        break;
                }
                else {
                    long[] bufferData = buffer.copyData();
                    for (int start = 0; start < bufferData.length; start+= evaluationBatchSize) {

                        long[] slice =  Arrays.copyOfRange(bufferData, start,
                                Math.min(start + evaluationBatchSize, bufferData.length));

                        var docIds = new CombinedDocIdList(slice);

                        if (!queue.offer(docIds, Math.max(1, budget.timeLeft()), TimeUnit.MILLISECONDS))
                            break;

                    }
                }
            }
        } catch (RuntimeException | InterruptedException ex) {
            log.error("Exception in lookup thread", ex);
        } finally {
            buffer.dispose();
            lookupCountdown.countDown();
        }

        return evaluationJobs;
    }

    private void prepare(ArrayBlockingQueue<CombinedDocIdList> inputQueue, ArrayBlockingQueue<IndexResultRankingService.RankingData> outputQueue) {
        try {
            while (budget.hasTimeLeft() && (lookupCountdown.getCount() > 0 || !inputQueue.isEmpty())) {
                var docIds = inputQueue.poll(Math.clamp(budget.timeLeft(), 1, 5), TimeUnit.MILLISECONDS);
                if (docIds == null) continue;
                long st = System.nanoTime();
                var preparedData = rankingService.prepareRankingData(rankingContext, docIds, budget);
                long et = System.nanoTime();
                prepTime.addAndGet(et - st);
                if (!outputQueue.offer(preparedData, Math.max(1, budget.timeLeft()), TimeUnit.MILLISECONDS))
                    preparedData.close();
            }
        } catch (TimeoutException ex) {
            // This is normal
        } catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
        } finally {
            preparationCountdown.countDown();
        }
    }

    private void evaluate(ArrayBlockingQueue<IndexResultRankingService.RankingData> queue) {
        try {
            while (budget.hasTimeLeft() && (preparationCountdown.getCount() > 0 || !queue.isEmpty())) {
                var rankingData = queue.poll(Math.clamp(budget.timeLeft(), 1, 5), TimeUnit.MILLISECONDS);
                if (rankingData == null) continue;

                try (rankingData) {
                    long st =  System.nanoTime();
                    resultHeap.addAll(rankingService.rankResults(budget, rankingContext, rankingData, false));
                    long et = System.nanoTime();
                    valuationTime.addAndGet(et - st);
                }
            }
        } catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
        } finally {
            rankingCountdown.countDown();
        }
    }

    public int itemsProcessed() {
        return resultHeap.getItemsProcessed();
    }

}
