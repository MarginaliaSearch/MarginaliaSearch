package nu.marginalia.crawl;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.ProcessConfigurationModule;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.DomainProber;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.spec.CrawlSpecProvider;
import nu.marginalia.crawl.spec.DbCrawlSpecProvider;
import nu.marginalia.crawl.spec.ParquetCrawlSpecProvider;
import nu.marginalia.crawl.warc.WarcArchiverFactory;
import nu.marginalia.crawl.warc.WarcArchiverIf;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.io.CrawlerOutputFile;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecordFileWriter;
import nu.marginalia.crawlspec.CrawlSpecFileNames;
import nu.marginalia.service.ProcessMainClass;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.util.SimpleBlockingThreadPool;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static nu.marginalia.mqapi.ProcessInboxNames.CRAWLER_INBOX;

public class CrawlerMain extends ProcessMainClass {
    private final static Logger logger = LoggerFactory.getLogger(CrawlerMain.class);

    private final UserAgent userAgent;
    private final ProcessHeartbeatImpl heartbeat;
    private final MessageQueueFactory messageQueueFactory;
    private final DomainProber domainProber;
    private final FileStorageService fileStorageService;
    private final DbCrawlSpecProvider dbCrawlSpecProvider;
    private final AnchorTagsSourceFactory anchorTagsSourceFactory;
    private final WarcArchiverFactory warcArchiverFactory;
    private final Gson gson;
    private final int node;
    private final SimpleBlockingThreadPool pool;

    private final Map<String, String> processingIds = new ConcurrentHashMap<>();

    final AbortMonitor abortMonitor = AbortMonitor.getInstance();

    volatile int totalTasks;
    final AtomicInteger tasksDone = new AtomicInteger(0);
    private HttpFetcherImpl fetcher;

    @Inject
    public CrawlerMain(UserAgent userAgent,
                       ProcessHeartbeatImpl heartbeat,
                       MessageQueueFactory messageQueueFactory, DomainProber domainProber,
                       FileStorageService fileStorageService,
                       ProcessConfiguration processConfiguration,
                       DbCrawlSpecProvider dbCrawlSpecProvider,
                       AnchorTagsSourceFactory anchorTagsSourceFactory,
                       WarcArchiverFactory warcArchiverFactory,
                       Gson gson) {
        this.userAgent = userAgent;
        this.heartbeat = heartbeat;
        this.messageQueueFactory = messageQueueFactory;
        this.domainProber = domainProber;
        this.fileStorageService = fileStorageService;
        this.dbCrawlSpecProvider = dbCrawlSpecProvider;
        this.anchorTagsSourceFactory = anchorTagsSourceFactory;
        this.warcArchiverFactory = warcArchiverFactory;
        this.gson = gson;
        this.node = processConfiguration.node();

        pool = new SimpleBlockingThreadPool("CrawlerPool",
                Integer.getInteger("crawler.poolSize", 256),
                1);

        fetcher = new HttpFetcherImpl(userAgent,
                new Dispatcher(),
                new ConnectionPool(5, 10, TimeUnit.SECONDS)
        );
    }

    public static void main(String... args) throws Exception {

        if (!AbortMonitor.getInstance().isAlive()) {
            System.err.println("Remove abort file first");
            return;
        }

        // Prevent Java from caching DNS lookups forever (filling up the system RAM as a result)
        Security.setProperty("networkaddress.cache.ttl" , "3600");

        // This must run *early*
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());

        // If these aren't set properly, the JVM will hang forever on some requests
        System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
        System.setProperty("sun.net.client.defaultReadTimeout", "30000");

        // We don't want to use too much memory caching sessions for https
        System.setProperty("javax.net.ssl.sessionCacheSize", "2048");

        try {
            Injector injector = Guice.createInjector(
                    new CrawlerModule(),
                    new ProcessConfigurationModule("crawler"),
                    new DatabaseModule(false)
            );
            var crawler = injector.getInstance(CrawlerMain.class);

            var instructions = crawler.fetchInstructions();
            try {
                crawler.run(instructions.specProvider, instructions.outputDir);
                instructions.ok();
            } catch (Exception ex) {
                logger.error("Crawler failed", ex);
                instructions.err();
            }

            TimeUnit.SECONDS.sleep(5);
        }
        catch (Exception ex) {
            logger.error("Uncaught exception", ex);
        }
        System.exit(0);
    }

    public void run(CrawlSpecProvider specProvider, Path outputDir) throws Exception {

        heartbeat.start();

        // First a validation run to ensure the file is all good to parse
        totalTasks = specProvider.totalCount();
        if (totalTasks == 0) {
            // This is an error state, and we should make noise about it
            throw new IllegalStateException("No crawl tasks found, refusing to continue");
        }
        logger.info("Queued {} crawl tasks, let's go", totalTasks);

        try (WorkLog workLog = new WorkLog(outputDir.resolve("crawler.log"));
             WarcArchiverIf warcArchiver = warcArchiverFactory.get(outputDir);
             AnchorTagsSource anchorTagsSource = anchorTagsSourceFactory.create(specProvider.getDomains())
        ) {
            try (var specStream = specProvider.stream()) {
                specStream
                        .takeWhile((e) -> abortMonitor.isAlive())
                        .filter(e -> !workLog.isJobFinished(e.domain))
                        .filter(e -> processingIds.put(e.domain, "") == null)
                        .map(e -> new CrawlTask(e, anchorTagsSource, outputDir, warcArchiver, workLog))
                        .forEach(pool::submitQuietly);
            }

            logger.info("Shutting down the pool, waiting for tasks to complete...");

            pool.shutDown();
            int activePoolCount = pool.getActiveCount();

            while (!pool.awaitTermination(5, TimeUnit.HOURS)) {
                int newActivePoolCount = pool.getActiveCount();
                if (activePoolCount == newActivePoolCount) {
                    logger.warn("Aborting the last {} jobs of the crawl, taking too long", newActivePoolCount);
                    pool.shutDownNow();
                } else {
                    activePoolCount = newActivePoolCount;
                }
            }

        }
        catch (Exception ex) {
            logger.warn("Exception in crawler", ex);
        }
        finally {
            heartbeat.shutDown();
        }
    }

    class CrawlTask implements SimpleBlockingThreadPool.Task {

        private final CrawlSpecRecord specification;

        private final String domain;
        private final String id;

        private final AnchorTagsSource anchorTagsSource;
        private final Path outputDir;
        private final WarcArchiverIf warcArchiver;
        private final WorkLog workLog;

        CrawlTask(CrawlSpecRecord specification,
                  AnchorTagsSource anchorTagsSource,
                  Path outputDir,
                  WarcArchiverIf warcArchiver,
                  WorkLog workLog) {
            this.specification = specification;
            this.anchorTagsSource = anchorTagsSource;
            this.outputDir = outputDir;
            this.warcArchiver = warcArchiver;
            this.workLog = workLog;

            this.domain = specification.domain;
            this.id = Integer.toHexString(domain.hashCode());
        }

        @Override
        public void run() throws Exception {

            Path newWarcFile = CrawlerOutputFile.createWarcPath(outputDir, id, domain, CrawlerOutputFile.WarcFileVersion.LIVE);
            Path tempFile = CrawlerOutputFile.createWarcPath(outputDir, id, domain, CrawlerOutputFile.WarcFileVersion.TEMP);
            Path parquetFile = CrawlerOutputFile.createParquetPath(outputDir, id, domain);

            if (Files.exists(newWarcFile)) {
                Files.move(newWarcFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            else {
                Files.deleteIfExists(tempFile);
            }

            try (var warcRecorder = new WarcRecorder(newWarcFile); // write to a temp file for now
                 var retriever = new CrawlerRetreiver(fetcher, domainProber, specification, warcRecorder);
                 CrawlDataReference reference = getReference())
            {
                Thread.currentThread().setName("crawling:" + domain);

                var domainLinks = anchorTagsSource.getAnchorTags(domain);

                if (Files.exists(tempFile)) {
                    retriever.syncAbortedRun(tempFile);
                    Files.delete(tempFile);
                }

                int size = retriever.fetch(domainLinks, reference);

                // Delete the reference crawl data if it's not the same as the new one
                // (mostly a case when migrating from legacy->warc)
                reference.delete();

                CrawledDocumentParquetRecordFileWriter
                        .convertWarc(domain, userAgent, newWarcFile, parquetFile);

                warcArchiver.consumeWarc(newWarcFile, domain);

                workLog.setJobToFinished(domain, parquetFile.toString(), size);
                heartbeat.setProgress(tasksDone.incrementAndGet() / (double) totalTasks);

                logger.info("Fetched {}", domain);
            } catch (Exception e) {
                logger.error("Error fetching domain " + domain, e);
            }
            finally {
                // We don't need to double-count these; it's also kept int he workLog
                processingIds.remove(domain);
                Thread.currentThread().setName("[idle]");

                Files.deleteIfExists(newWarcFile);
                Files.deleteIfExists(tempFile);
            }
        }

        private CrawlDataReference getReference() {
            try {
                return new CrawlDataReference(CrawledDomainReader.createDataStream(outputDir, domain, id));
            } catch (IOException e) {
                logger.debug("Failed to read previous crawl data for {}", specification.domain);
                return new CrawlDataReference();
            }
        }

    }



    private static class CrawlRequest {
        private final CrawlSpecProvider specProvider;
        private final Path outputDir;
        private final MqMessage message;
        private final MqSingleShotInbox inbox;

        CrawlRequest(CrawlSpecProvider specProvider, Path outputDir, MqMessage message, MqSingleShotInbox inbox) {
            this.message = message;
            this.inbox = inbox;
            this.specProvider = specProvider;
            this.outputDir = outputDir;
        }


        public void ok() {
            inbox.sendResponse(message, MqInboxResponse.ok());
        }
        public void err() {
            inbox.sendResponse(message, MqInboxResponse.err());
        }

    }

    private CrawlRequest fetchInstructions() throws Exception {

        var inbox = messageQueueFactory.createSingleShotInbox(CRAWLER_INBOX, node, UUID.randomUUID());

        logger.info("Waiting for instructions");
        var msgOpt = getMessage(inbox, nu.marginalia.mqapi.crawling.CrawlRequest.class.getSimpleName());
        var msg = msgOpt.orElseThrow(() -> new RuntimeException("No message received"));

        var request = gson.fromJson(msg.payload(), nu.marginalia.mqapi.crawling.CrawlRequest.class);

        CrawlSpecProvider specProvider;

        if (request.specStorage != null) {
            var specData = fileStorageService.getStorage(request.specStorage);
            var parquetProvider = new ParquetCrawlSpecProvider(CrawlSpecFileNames.resolve(specData));

            // Ensure the parquet domains are loaded into the database to avoid
            // rare data-loss scenarios
            dbCrawlSpecProvider.ensureParquetDomainsLoaded(parquetProvider);

            specProvider = parquetProvider;
        }
        else {
            specProvider = dbCrawlSpecProvider;
        }

        var crawlData = fileStorageService.getStorage(request.crawlStorage);

        return new CrawlRequest(
                specProvider,
                crawlData.asPath(),
                msg,
                inbox);
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

}
