package nu.marginalia.crawl;

import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.work_log.WorkLog;
import nu.marginalia.crawl_plan.CrawlPlanLoader;
import nu.marginalia.crawl_plan.CrawlPlan;
import nu.marginalia.crawling.io.CrawledDomainWriter;
import nu.marginalia.crawling.model.CrawlingSpecification;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.HttpFetcher;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.internal.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.*;

public class CrawlerMain implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CrawlPlan plan;
    private final Path crawlDataDir;

    private final WorkLog workLog;

    private final ConnectionPool connectionPool = new ConnectionPool(5, 10, TimeUnit.SECONDS);

    private final Dispatcher dispatcher = new Dispatcher(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", true)));

    private final UserAgent userAgent;
    private final ThreadPoolExecutor pool;
    final int poolSize = Integer.getInteger("crawler.pool-size", 512);
    final int poolQueueSize = 32;

    AbortMonitor abortMonitor = AbortMonitor.getInstance();
    Semaphore taskSem = new Semaphore(poolSize);

    public CrawlerMain(CrawlPlan plan) throws Exception {
        this.plan = plan;
        this.userAgent = WmsaHome.getUserAgent();

        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(poolQueueSize);
        pool = new ThreadPoolExecutor(poolSize/128, poolSize, 5, TimeUnit.MINUTES, queue); // maybe need to set -Xss for JVM to deal with this?

        workLog = plan.createCrawlWorkLog();
        crawlDataDir = plan.crawl.getDir();
    }

    public static void main(String... args) throws Exception {
        if (!AbortMonitor.getInstance().isAlive()) {
            System.err.println("Remove abort file first");
            return;
        }

        if (args.length != 1) {
            System.err.println("Arguments: crawl-plan.yaml");
            System.exit(0);
        }
        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        try (var crawler = new CrawlerMain(plan)) {
            crawler.run();
        }

        System.exit(0);
    }

    public void run() throws InterruptedException {
        // First a validation run to ensure the file is all good to parse
        logger.info("Validating JSON");
        plan.forEachCrawlingSpecification(unused -> {});

        logger.info("Let's go");

        plan.forEachCrawlingSpecification(this::startCrawlTask);
    }

    private void startCrawlTask(CrawlingSpecification crawlingSpecification) {
        if (abortMonitor.isAlive()) {
            try {
                taskSem.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            pool.execute(() -> {
                try {
                    fetchDomain(crawlingSpecification);
                }
                finally {
                    taskSem.release();
                }
            });
        }
    }

    private void fetchDomain(CrawlingSpecification specification) {
        if (workLog.isJobFinished(specification.id))
            return;


        HttpFetcher fetcher = new HttpFetcher(userAgent.uaString(), dispatcher, connectionPool);
        try (CrawledDomainWriter writer = new CrawledDomainWriter(crawlDataDir, specification.domain, specification.id)) {
            var retreiver = new CrawlerRetreiver(fetcher, specification, writer::accept);

            int size = retreiver.fetch();

            workLog.setJobToFinished(specification.id, writer.getOutputFile().toString(), size);

            logger.info("Fetched {}", specification.domain);
        } catch (Exception e) {
            logger.error("Error fetching domain", e);
        }
    }

    public void close() throws Exception {
        logger.info("Awaiting termination");
        pool.shutdown();

        while (!pool.awaitTermination(1, TimeUnit.SECONDS));
        logger.info("All finished");

        workLog.close();
        dispatcher.executorService().shutdownNow();


    }

}
