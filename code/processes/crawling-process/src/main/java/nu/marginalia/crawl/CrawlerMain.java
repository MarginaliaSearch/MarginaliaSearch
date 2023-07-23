package nu.marginalia.crawl;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.io.CrawlerOutputFile;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.DatabaseModule;
import plan.CrawlPlan;
import nu.marginalia.crawling.io.CrawledDomainWriter;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.internal.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static nu.marginalia.mqapi.ProcessInboxNames.CRAWLER_INBOX;

public class CrawlerMain implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Path crawlDataDir;
    private WorkLog workLog;

    private final ProcessHeartbeat heartbeat;
    private final ConnectionPool connectionPool = new ConnectionPool(5, 10, TimeUnit.SECONDS);

    private final Dispatcher dispatcher = new Dispatcher(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", true)));

    private final UserAgent userAgent;
    private final MessageQueueFactory messageQueueFactory;
    private final FileStorageService fileStorageService;
    private final Gson gson;
    private final ThreadPoolExecutor pool;

    public final CrawlLimiter crawlLimiter = new CrawlLimiter();
    private final Set<String> processedIds = new HashSet<>();

    final AbortMonitor abortMonitor = AbortMonitor.getInstance();

    volatile int totalTasks;
    final AtomicInteger tasksDone = new AtomicInteger(0);

    @Inject
    public CrawlerMain(UserAgent userAgent,
                       ProcessHeartbeat heartbeat,
                       MessageQueueFactory messageQueueFactory,
                       FileStorageService fileStorageService,
                       Gson gson) {
        this.heartbeat = heartbeat;
        this.userAgent = userAgent;
        this.messageQueueFactory = messageQueueFactory;
        this.fileStorageService = fileStorageService;
        this.gson = gson;

        // maybe need to set -Xss for JVM to deal with this?
        pool = new ThreadPoolExecutor(
                CrawlLimiter.maxPoolSize /128,
                CrawlLimiter.maxPoolSize,
                5, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(32)
        );
    }

    public static void main(String... args) throws Exception {
        if (!AbortMonitor.getInstance().isAlive()) {
            System.err.println("Remove abort file first");
            return;
        }

        // This must run *early*
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());

        // If these aren't set properly, the JVM will hang forever on some requests
        System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
        System.setProperty("sun.net.client.defaultReadTimeout", "30000");

        Injector injector = Guice.createInjector(
                new CrawlerModule(),
                new DatabaseModule()
        );
        var crawler = injector.getInstance(CrawlerMain.class);

        var instructions = crawler.fetchInstructions();
        try {
            crawler.run(instructions.getPlan());
            instructions.ok();
        }
        catch (Exception ex) {
            System.err.println("Crawler failed");
            ex.printStackTrace();
            instructions.err();
        }

        TimeUnit.SECONDS.sleep(5);

        System.exit(0);
    }

    public void run(CrawlPlan plan) throws InterruptedException, IOException {

        heartbeat.start();
        try {
            // First a validation run to ensure the file is all good to parse
            logger.info("Validating JSON");


            workLog = plan.createCrawlWorkLog();
            crawlDataDir = plan.crawl.getDir();

            int countTotal = 0;
            for (var unused : plan.crawlingSpecificationIterable()) {
                countTotal++;
            }
            totalTasks = countTotal;

            logger.info("Let's go");

            for (var spec : plan.crawlingSpecificationIterable()) {
                startCrawlTask(plan, spec);
            }

            pool.shutdown();
            do {
                System.out.println("Waiting for pool to terminate... " + pool.getActiveCount() + " remaining");
            } while (!pool.awaitTermination(60, TimeUnit.SECONDS));
        }
        finally {
            heartbeat.shutDown();
        }
    }

    CrawledDomainReader reader = new CrawledDomainReader();


    private void startCrawlTask(CrawlPlan plan, CrawlingSpecification crawlingSpecification) {

        if (!processedIds.add(crawlingSpecification.id)) {

            // This is a duplicate id, so we ignore it.  Otherwise we'd end crawling the same site twice,
            // and if we're really unlucky, we might end up writing to the same output file from multiple
            // threads with complete bit salad as a result.

            logger.error("Ignoring duplicate id: {}", crawlingSpecification.id);
            return;
        }

        if (!abortMonitor.isAlive()) {
            return;
        }

        try {
            crawlLimiter.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        pool.execute(() -> {
            try {
                fetchDomain(crawlingSpecification);
                heartbeat.setProgress(tasksDone.incrementAndGet() / (double) totalTasks);
            }
            finally {
                crawlLimiter.release();
            }
        });
    }


    private void fetchDomain(CrawlingSpecification specification) {
        if (workLog.isJobFinished(specification.id))
            return;

        HttpFetcher fetcher = new HttpFetcherImpl(userAgent.uaString(), dispatcher, connectionPool);


        try (CrawledDomainWriter writer = new CrawledDomainWriter(crawlDataDir, specification)) {
            var retreiver = new CrawlerRetreiver(fetcher, specification, writer::accept);

            CrawlDataReference reference = getReference(specification);

            int size = retreiver.fetch(reference);

            workLog.setJobToFinished(specification.id, writer.getOutputFile().toString(), size);

            logger.info("Fetched {}", specification.domain);
        } catch (Exception e) {
            logger.error("Error fetching domain", e);
        }
    }

    private CrawlDataReference getReference(CrawlingSpecification specification) {
        try {
            var iterator = reader.createIterator(crawlDataDir, specification);
            return new CrawlDataReference(iterator);
        } catch (IOException e) {
            logger.warn("Failed to read previous crawl data for {}", specification.domain);
            return new CrawlDataReference();
        }
    }

    private static class CrawlRequest {
        private final CrawlPlan plan;
        private final MqMessage message;
        private final MqSingleShotInbox inbox;

        CrawlRequest(CrawlPlan plan, MqMessage message, MqSingleShotInbox inbox) {
            this.plan = plan;
            this.message = message;
            this.inbox = inbox;
        }

        public CrawlPlan getPlan() {
            return plan;
        }

        public void ok() {
            inbox.sendResponse(message, MqInboxResponse.ok());
        }
        public void err() {
            inbox.sendResponse(message, MqInboxResponse.err());
        }

    }

    private CrawlRequest fetchInstructions() throws Exception {

        var inbox = messageQueueFactory.createSingleShotInbox(CRAWLER_INBOX, UUID.randomUUID());

        var msgOpt = getMessage(inbox, nu.marginalia.mqapi.crawling.CrawlRequest.class.getSimpleName());
        var msg = msgOpt.orElseThrow(() -> new RuntimeException("No message received"));

        var request = gson.fromJson(msg.payload(), nu.marginalia.mqapi.crawling.CrawlRequest.class);

        var specData = fileStorageService.getStorage(request.specStorage);
        var crawlData = fileStorageService.getStorage(request.crawlStorage);

        var plan = new CrawlPlan(specData.asPath().resolve("crawler.spec").toString(),
                new CrawlPlan.WorkDir(crawlData.path(), "crawler.log"),
                null);

        return new CrawlRequest(plan, msg, inbox);
    }

    private Optional<MqMessage> getMessage(MqSingleShotInbox inbox, String expectedFunction) throws SQLException, InterruptedException {
        var opt = inbox.waitForMessage(30, TimeUnit.SECONDS);
        if (opt.isPresent()) {
            if (!opt.get().function().equals(expectedFunction)) {
                throw new RuntimeException("Unexpected function: " + opt.get().function());
            }
            return opt;
        }
        else {
            var stolenMessage = inbox.stealMessage(msg -> msg.function().equals(expectedFunction));
            stolenMessage.ifPresent(mqMessage -> logger.info("Stole message {}", mqMessage));
            return stolenMessage;
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
