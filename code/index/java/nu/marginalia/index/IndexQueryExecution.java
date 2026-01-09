package nu.marginalia.index;

import io.prometheus.metrics.core.metrics.Gauge;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.asyncio.RingBufferSPNC;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.CombinedTermMetadata;
import nu.marginalia.index.model.RankableDocument;
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
    private static final boolean printDebugSummary = Boolean.getBoolean("index.printDebugSummary");

    private static final int maxSimultaneousQueries = Integer.getInteger("index.maxSimultaneousQueries", 4);
    private static final Semaphore simultaneousRequests = new Semaphore(maxSimultaneousQueries);

    // Since most NVMe drives have a maximum read size of 128 KB, and most small reads are 512B
    // this should probably be 128*1024 / 512 = 256 to reduce queue depth and optimize tail latency
    private static final int evaluationBatchSize = 256;

    private static final int lookupBatchSize = SkipListConstants.MAX_RECORDS_PER_BLOCK;

    private static final ExecutorService threadPool =
            new ThreadPoolExecutor(indexValuationThreads, 256, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    private static final Logger log = LoggerFactory.getLogger(IndexQueryExecution.class);

    private final CombinedIndexReader currentIndex;
    private final String nodeName;
    private final IndexResultRankingService rankingService;

    private final SearchContext rankingContext;
    private final List<IndexQuery> queries;
    private final IndexSearchBudget budget;
    private final ResultPriorityQueue resultHeap;

    private final CountDownLatch lookupCountdown;
    private final CountDownLatch preparationCountdown;
    private final CountDownLatch spansCountdown;
    private final CountDownLatch termsCountdown;
    private final CountDownLatch rankingCountdown;

    private final ArrayBlockingQueue<CombinedDocIdList> preparationQueue = new ArrayBlockingQueue<>(2);

    private final RingBufferSPNC<RankableDocument> termPositionRetrievalQueue = new RingBufferSPNC<>(32);
    private final RingBufferSPNC<RankableDocument> spanRetrievalQueue  = new RingBufferSPNC<>(32);
    private final RingBufferSPNC<RankableDocument> rankingQueue  = new RingBufferSPNC<>(32);

    private final int limitTotal;
    private final int limitByDomain;

    private static final Gauge metric_index_lookup_time_s = Gauge.builder()
            .labelNames("node")
            .name("index_exec_lookup_time_s")
            .help("Time in query spent on lookups")
            .register();

    private static final Gauge metric_index_prep_time_s = Gauge.builder()
            .labelNames("node")
            .name("index_exec_prep_time_s")
            .help("Time in query spent retrieving positions and spans")
            .register();

    private static final Gauge metric_index_rank_time_s = Gauge.builder()
            .labelNames("node")
            .name("index_exec_ranking_time_s")
            .help("Time in query spent on ranking")
            .register();

    private static final Gauge metric_index_documents_ranked = Gauge.builder()
            .labelNames("node")
            .name("index_exec_documents_ranked")
            .help("Number of documents ranked")
            .register();

    private static final Gauge index_execution_rejected_queries = Gauge.builder()
            .labelNames("node")
            .name("index_execution_rejected_queries")
            .help("Number of queries rejected to avoid backpressure")
            .register();

    public static class TooManySimultaneousQueriesException extends Exception {
        @Override
        public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[0];
        }
    }

    public IndexQueryExecution(CombinedIndexReader currentIndex,
                               IndexResultRankingService rankingService,
                               SearchContext rankingContext,
                               int serviceNode) {
        this.currentIndex = currentIndex;
        this.nodeName = Integer.toString(serviceNode);
        this.rankingService = rankingService;
        this.rankingContext = rankingContext;

        resultHeap = new ResultPriorityQueue(rankingContext.limitTotal * 2);

        budget = rankingContext.budget;
        limitByDomain = rankingContext.limitByDomain;
        limitTotal = rankingContext.limitTotal;

        queries = currentIndex.createQueries(rankingContext);

        lookupCountdown = new CountDownLatch(queries.size());
        rankingCountdown = new CountDownLatch(indexValuationThreads * 2);
        spansCountdown = new CountDownLatch(1);
        preparationCountdown = new CountDownLatch(1);
        termsCountdown = new CountDownLatch(1);
    }

    public List<RpcDecoratedResultItem> run() throws InterruptedException, SQLException, TooManySimultaneousQueriesException {


        if (!simultaneousRequests.tryAcquire(budget.timeLeft() / 2, TimeUnit.MILLISECONDS)) {
            index_execution_rejected_queries.inc();
            throw new TooManySimultaneousQueriesException();
        }
        try {
            for (IndexQuery query : queries) {
                threadPool.submit(() -> lookup(query));
            }

            threadPool.submit(this::prepare);
            threadPool.submit(this::getSpans);
            threadPool.submit(this::getPositions);

            // Spawn lookup tasks for each query
            for (int i = 0; i < rankingCountdown.getCount(); i++) {
                threadPool.submit(this::evaluate);
            }

            // Await lookup task termination
            lookupCountdown.await();
            preparationCountdown.await();
            spansCountdown.await();
            termsCountdown.await();
            rankingCountdown.await();
        }
        finally {
            simultaneousRequests.release();
        }

        if (printDebugSummary) {
            for (var query : queries) {
                query.printDebugInformation();
            }
        }

        metric_index_documents_ranked
                .labelValues(nodeName)
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
                        .labelValues(nodeName)
                        .inc((et - st)/1_000_000_000.);

                if (buffer.isEmpty())
                    continue;


                if (buffer.end <= evaluationBatchSize) {
                    var docIds = new CombinedDocIdList(buffer);

                    if (!preparationQueue.offer(docIds, Math.max(1, budget.timeLeft()), TimeUnit.MILLISECONDS))
                        break;
                }
                else {
                    long[] bufferData = buffer.copyData();
                    for (int start = 0; start < bufferData.length; start+= evaluationBatchSize) {

                        long[] slice =  Arrays.copyOfRange(bufferData, start,
                                Math.min(start + evaluationBatchSize, bufferData.length));

                        var docIds = new CombinedDocIdList(slice);

                        if (!preparationQueue.offer(docIds, Math.max(1, budget.timeLeft()), TimeUnit.MILLISECONDS))
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

    private void prepare() {
        try {
            while (budget.hasTimeLeft() && (lookupCountdown.getCount() > 0 || !preparationQueue.isEmpty())) {
                var docIds = preparationQueue.poll(Math.clamp(budget.timeLeft(), 1, 5), TimeUnit.MILLISECONDS);
                if (docIds == null) continue;

                long st = System.nanoTime();

                CombinedTermMetadata termMetadata = currentIndex.getTermMetadata(rankingContext, docIds);

                long et = System.nanoTime();
                metric_index_prep_time_s
                        .labelValues(nodeName)
                        .inc((et - st)/1_000_000_000.);

                boolean abort = false;

                for (int i = 0; i < docIds.size() && !abort; i++) {
                    long docId = docIds.at(i);

                    RankableDocument item = new RankableDocument(docId);

                    item.priorityTermsPresent = termMetadata.priorityTermsPresent(i);
                    item.termFlags = termMetadata.flagsForDoc(i);
                    item.positionOffsets = termMetadata.positionOffsetsForDoc(i);

                    if (!enqueue(item, spanRetrievalQueue))
                        break;
                }
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

    private void getSpans() {
        try {
            for (;;) {
                RankableDocument rankableDocument = getFromQueue(spanRetrievalQueue);

                if (rankableDocument == null) {
                    if (preparationCountdown.getCount() == 0)
                        return;
                    else
                        continue;
                }

                currentIndex.getDocumentSpans(rankableDocument.combinedDocumentId)
                        .thenAccept(spans -> {
                            rankableDocument.documentSpans = spans;
                            enqueue(rankableDocument, termPositionRetrievalQueue);
                        });
            }
        } catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
        } finally {
            spansCountdown.countDown();
        }
    }

    private void getPositions() {
        try {
            for (;;) {
                RankableDocument rankableDocument = getFromQueue(termPositionRetrievalQueue);

                if (rankableDocument == null) {
                    if (spansCountdown.getCount() == 0)
                        return;
                    else
                        continue;
                }

                currentIndex.getTermPositions(rankableDocument.positionOffsets)
                        .thenAccept(positions -> {
                            rankableDocument.positions = positions;
                            enqueue(rankableDocument, rankingQueue);
                        });
            }
        } catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
        } finally {
            termsCountdown.countDown();
        }
    }

    private void evaluate() {
        try {
            for (;;) {
                RankableDocument rankableDocument = getFromQueue(rankingQueue);

                if (rankableDocument == null) {
                    if ((termsCountdown.getCount() == 0))
                        return;
                    else
                        continue;
                }

                if (null != (rankableDocument.item = rankingService.calculateScore(null, currentIndex, rankingContext, rankableDocument)))
                    resultHeap.add(rankableDocument);
            }
        } catch (Exception ex) {
            if (!(ex.getCause() instanceof InterruptedException)) {
                log.error("Exception in lookup thread", ex);
            }  // suppress logging for interrupted ex
        } finally {
            rankingCountdown.countDown();
        }
    }

    private RankableDocument getFromQueue(RingBufferSPNC<RankableDocument> queue) {
        RankableDocument rankableDocument = null;
        for (int i = 0; rankableDocument == null && i < 1000; i++) {
            rankableDocument = queue.tryTake();
        }
        for (int i = 0; rankableDocument == null && i < 100_000; i++) {
            rankableDocument = queue.tryTake();
            Thread.yield();
        }
        return rankableDocument;
    }

    private boolean enqueue(RankableDocument item, RingBufferSPNC<RankableDocument> queue) {
        for (int iter = 0; !queue.put(item); iter++) {
            if (iter > 1000) {
                if ((iter & 0x100) != 0 && !budget.hasTimeLeft()) return false;
                Thread.yield();
            }
        }
        return true;
    }

    public int itemsProcessed() {
        return resultHeap.getItemsProcessed();
    }

}
