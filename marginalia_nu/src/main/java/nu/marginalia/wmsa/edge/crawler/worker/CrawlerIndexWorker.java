package nu.marginalia.wmsa.edge.crawler.worker;

import nu.marginalia.wmsa.edge.crawler.domain.DomainCrawlerFactory;
import nu.marginalia.wmsa.edge.crawler.worker.facade.TaskProvider;
import nu.marginalia.wmsa.edge.crawler.worker.results.DomainCrawlerWorkerResults;
import nu.marginalia.wmsa.edge.crawler.worker.results.InvalidTaskResult;
import nu.marginalia.wmsa.edge.crawler.worker.results.WorkerResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;

public class CrawlerIndexWorker implements Worker {
    private final DomainCrawlerFactory domainCrawlerFactory;
    private final TaskProvider taskProvider;
    private final IpBlockList blockList;

    private final LinkedBlockingQueue<WorkerResults> queue;
    private final int pass;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public CrawlerIndexWorker(
            DomainCrawlerFactory domainCrawlerFactory,
            TaskProvider taskProvider,
            IpBlockList blockList, LinkedBlockingQueue<WorkerResults> queue, int pass) {
        this.domainCrawlerFactory = domainCrawlerFactory;
        this.taskProvider = taskProvider;
        this.blockList = blockList;
        this.queue = queue;
        this.pass = pass;
    }

    @Override
    public void runCycle() throws InterruptedException {

        var ingress = taskProvider
                .getIndexTask(pass);

        if (ingress.isEmpty()) {
            wmsa_edge_crawler_idle_worker.inc();
            Thread.sleep(100);
            return;
        }
        try {
            if (ingress.rank > 0.25 && !blockList.isAllowed(ingress.domain)) {
                queue.put(new InvalidTaskResult(ingress.domain, "IP blocked"));
                logger.info("{} IP-blacklisted", ingress.domain);
                return;
            }

            long start = System.currentTimeMillis();

            var dc = domainCrawlerFactory.domainCrawler(ingress);
            var res = dc.crawl();

            wmsa_edge_crawler_thread_run_times.observe(System.currentTimeMillis() - start);

            queue.put(new DomainCrawlerWorkerResults(res));
        }
        catch (RuntimeException ex) {
            logger.warn("Leaking {}", ingress.domain);
            logger.error("Uncaught exception", ex);
        }
        catch (StackOverflowError er) {
            logger.error("Stack Overflow on  {}", ingress.domain);
            queue.put(new InvalidTaskResult(ingress.domain, "Stack overflow"));
        }
        catch (InterruptedException e) {
            throw e;
        }
    }

    @Override
    public void run() {
        for (;;) {
            try {
                runCycle();
            }
            catch (InterruptedException ex) {
                logger.error("Interrupted", ex);
                break;
            }
            catch (Exception ex) {
                logger.error("Uncaught exception in Fetcher thread", ex);
            }
        }
    }
}
