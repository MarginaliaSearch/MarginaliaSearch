package nu.marginalia.index;

import io.prometheus.client.Gauge;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.reverse.query.IndexQuery;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.skiplist.SkipListConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/** Performs an index query */
public class IndexQueryExecution {

    private static final int indexValuationThreads = Integer.getInteger("index.valuationThreads", 8);
    private static final int indexPreparationThreads = Integer.getInteger("index.preparationThreads", 2);

    // Since most NVMe drives have a maximum read size of 128 KB, and most small reads are 512B
    // this should probably be 128*1024 / 512 = 256 to reduce queue depth and optimize tail latency
    private static final int evaluationBatchSize = 256;

    // This should probably be SkipListConstants.BLOCK_SIZE / 16 in order to reduce the number of unnecessary read
    // operations per lookup and again optimize tail latency
    private static final int lookupBatchSize = SkipListConstants.BLOCK_SIZE / 16;

    private static final ExecutorService threadPool =
            new ThreadPoolExecutor(indexValuationThreads, 256,
                    60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    private static final Logger log = LoggerFactory.getLogger(IndexQueryExecution.class);

    private final String nodeName;
    private final IndexResultRankingService rankingService;

    private final SearchContext rankingContext;
    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;
    private final ResultPriorityQueue resultHeap;

    private final CountDownLatch lookupCountdown;
    private final CountDownLatch preparationCountdown;
    private final CountDownLatch rankingCountdown;

    private final ArrayBlockingQueue<CombinedDocIdList> fullPreparationQueue = new ArrayBlockingQueue<>(1);
    private final ArrayBlockingQueue<CombinedDocIdList> priorityPreparationQueue = new ArrayBlockingQueue<>(1);
    private final ArrayBlockingQueue<IndexResultRankingService.RankingData> fullEvaluationQueue = new ArrayBlockingQueue<>(32);
    private final ArrayBlockingQueue<IndexResultRankingService.RankingData> priorityEvaluationQueue = new ArrayBlockingQueue<>(32);

    private final int limitTotal;
    private final int limitByDomain;

    private static final Gauge metric_index_lookup_time_s = Gauge.build()
            .labelNames("node")
            .name("index_exec_lookup_time_s")
            .help("Time in query spent on lookups")
            .register();

    private static final Gauge metric_index_prep_time_s = Gauge.build()
            .labelNames("node")
            .name("index_exec_prep_time_s")
            .help("Time in query spent retrieving positions and spans")
            .register();

    private static final Gauge metric_index_rank_time_s = Gauge.build()
            .labelNames("node")
            .name("index_exec_ranking_time_s")
            .help("Time in query spent on ranking")
            .register();

    private static final Gauge metric_index_documents_ranked = Gauge.build()
            .labelNames("node")
            .name("index_exec_documents_ranked")
            .help("Number of documents ranked")
            .register();



    public IndexQueryExecution(CombinedIndexReader currentIndex,
                               IndexResultRankingService rankingService,
                               SearchContext rankingContext,
                               int serviceNode) {
        this.nodeName = Integer.toString(serviceNode);
        this.rankingService = rankingService;
        this.rankingContext = rankingContext;

        resultHeap = new ResultPriorityQueue(rankingContext.fetchSize);

        budget = rankingContext.budget;
        limitByDomain = rankingContext.limitByDomain;
        limitTotal = rankingContext.limitTotal;

        queries = currentIndex.createQueries(rankingContext);

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

        metric_index_documents_ranked
                .labels(nodeName)
                .inc(1000. * resultHeap.getItemsProcessed() / budget.getLimitTime());

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
                metric_index_lookup_time_s
                        .labels(nodeName)
                        .inc((et - st)/1_000_000_000.);

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
                var preparedData = rankingService.prepareRankingData(rankingContext, docIds);
                long et = System.nanoTime();
                metric_index_prep_time_s
                        .labels(nodeName)
                        .inc((et - st)/1_000_000_000.);

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
                    resultHeap.addAll(rankingService.rankResults(rankingContext, rankingData, false));
                    long et = System.nanoTime();

                    metric_index_rank_time_s
                            .labels(nodeName)
                            .inc((et - st)/1_000_000_000.);
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
