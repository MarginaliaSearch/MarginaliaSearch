package nu.marginalia.wmsa.edge.crawling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nu.marginalia.wmsa.configuration.UserAgent;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import nu.marginalia.wmsa.edge.crawling.retreival.CrawlerRetreiver;
import nu.marginalia.wmsa.edge.crawling.retreival.HttpFetcher;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import okhttp3.Dispatcher;
import okhttp3.internal.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CrawlerMain implements AutoCloseable {
    public static Gson gson = new GsonBuilder().create();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path inputSpec;

    private final WorkLog workLog;
    private final CrawledDomainWriter domainWriter;

    private final int numberOfThreads;
    private final ParallelPipe<CrawlingSpecification, CrawledDomain> pipe;
    private final Dispatcher dispatcher = new Dispatcher(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", true)));

    private final UserAgent userAgent;

    public CrawlerMain(EdgeCrawlPlan plan) throws Exception {
        this.inputSpec = plan.getJobSpec();
        this.numberOfThreads = 512;
        this.userAgent = WmsaHome.getUserAgent();

        workLog = new WorkLog(plan.crawl.getLogFile());
        domainWriter = new CrawledDomainWriter(plan.crawl.getDir());

        Semaphore sem = new Semaphore(250_000);

        pipe = new ParallelPipe<>("Crawler", numberOfThreads, 2, 1) {
            @Override
            protected CrawledDomain onProcess(CrawlingSpecification crawlingSpecification) throws Exception {
                int toAcquire = crawlingSpecification.urls.size();
                sem.acquire(toAcquire);
                try {
                    return fetchDomain(crawlingSpecification);
                }
                finally {
                    sem.release(toAcquire);
                }
            }

            @Override
            protected void onReceive(CrawledDomain crawledDomain) throws IOException {
                writeDomain(crawledDomain);
            }
        };
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

        // TODO (2022-05-24): Some thread isn't set to daemon mode, need to explicitly harakiri the process, find why?
        System.exit(0);
    }

    private CrawledDomain fetchDomain(CrawlingSpecification specification) {
        if (workLog.isJobFinished(specification.id))
            return null;

        var fetcher = new HttpFetcher(userAgent.uaString(), dispatcher);

        try {
            var retreiver = new CrawlerRetreiver(fetcher, specification);

            return retreiver.fetch();
        } catch (Exception e) {
            logger.error("Error fetching domain", e);
            return null;
        }
    }

    private void writeDomain(CrawledDomain crawledDomain) throws IOException {
        String name = domainWriter.accept(crawledDomain);
        workLog.setJobToFinished(crawledDomain.id, name, crawledDomain.size());
    }

    public void run() throws InterruptedException {
        // First a validation run to ensure the file is all good to parse

        logger.info("Validating JSON");
        CrawlerSpecificationLoader.readInputSpec(inputSpec, spec -> {});

        logger.info("Starting pipe");
        CrawlerSpecificationLoader.readInputSpec(inputSpec, pipe::accept);

        if (!AbortMonitor.getInstance().isAlive()) {
            logger.info("Aborting");
            pipe.clearQueues();
        }
        else {
            logger.info("All jobs queued, waiting for pipe to finish");
        }
        pipe.join();

        logger.info("All finished");
    }

    public void close() throws Exception {
        workLog.close();
        dispatcher.executorService().shutdownNow();
    }

}
