package nu.marginalia.crawl;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.logic.DomainLocks;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainProber;
import nu.marginalia.crawl.warc.WarcArchiverFactory;
import nu.marginalia.crawl.warc.WarcArchiverIf;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.io.CrawlerOutputFile;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.slop.SlopCrawlDataRecord;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static nu.marginalia.mqapi.ProcessInboxNames.CRAWLER_INBOX;

public class CrawlerMain extends ProcessMainClass {
    private final static Logger logger = LoggerFactory.getLogger(CrawlerMain.class);

    private final UserAgent userAgent;
    private final ProcessHeartbeatImpl heartbeat;
    private final DomainProber domainProber;
    private final FileStorageService fileStorageService;
    private final AnchorTagsSourceFactory anchorTagsSourceFactory;
    private final WarcArchiverFactory warcArchiverFactory;
    private final HikariDataSource dataSource;
    private final DomainBlacklist blacklist;
    private final int node;
    private final SimpleBlockingThreadPool pool;

    private final DomainLocks domainLocks = new DomainLocks();

    private final Map<String, CrawlTask> pendingCrawlTasks = new ConcurrentHashMap<>();

    private final LinkedBlockingQueue<CrawlTask> retryQueue = new LinkedBlockingQueue<>();

    private final AtomicInteger tasksDone = new AtomicInteger(0);
    private final HttpFetcherImpl fetcher;

    private int totalTasks = 1;

    private static final double URL_GROWTH_FACTOR = Double.parseDouble(System.getProperty("crawler.crawlSetGrowthFactor", "1.25"));
    private static final int MIN_URLS_PER_DOMAIN = Integer.getInteger("crawler.minUrlsPerDomain", 100);
    private static final int MID_URLS_PER_DOMAIN = Integer.getInteger("crawler.minUrlsPerDomain", 2_000);
    private static final int MAX_URLS_PER_DOMAIN = Integer.getInteger("crawler.maxUrlsPerDomain", 10_000);


    @Inject
    public CrawlerMain(UserAgent userAgent,
                       HttpFetcherImpl httpFetcher,
                       ProcessHeartbeatImpl heartbeat,
                       MessageQueueFactory messageQueueFactory, DomainProber domainProber,
                       FileStorageService fileStorageService,
                       ProcessConfiguration processConfiguration,
                       AnchorTagsSourceFactory anchorTagsSourceFactory,
                       WarcArchiverFactory warcArchiverFactory,
                       HikariDataSource dataSource,
                       DomainBlacklist blacklist,
                       Gson gson) throws InterruptedException {

        super(messageQueueFactory, processConfiguration, gson, CRAWLER_INBOX);

        this.userAgent = userAgent;
        this.fetcher = httpFetcher;
        this.heartbeat = heartbeat;
        this.domainProber = domainProber;
        this.fileStorageService = fileStorageService;
        this.anchorTagsSourceFactory = anchorTagsSourceFactory;
        this.warcArchiverFactory = warcArchiverFactory;
        this.dataSource = dataSource;
        this.blacklist = blacklist;
        this.node = processConfiguration.node();

        SimpleBlockingThreadPool.ThreadType threadType;
        if (Boolean.getBoolean("crawler.useVirtualThreads")) {
            threadType = SimpleBlockingThreadPool.ThreadType.VIRTUAL;
        }
        else {
            threadType = SimpleBlockingThreadPool.ThreadType.PLATFORM;
        }

        pool = new SimpleBlockingThreadPool("CrawlerPool",
                Integer.getInteger("crawler.poolSize", 256),
                1,
                threadType);


        // Wait for the blacklist to be loaded before starting the crawl
        blacklist.waitUntilLoaded();
    }

    public static void main(String... args) throws Exception {

        // Prevent Java from caching DNS lookups forever (filling up the system RAM as a result)
        Security.setProperty("networkaddress.cache.ttl" , "3600");

        // This must run *early*
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());

        // If these aren't set properly, the JVM will hang forever on some requests
        System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
        System.setProperty("sun.net.client.defaultReadTimeout", "30000");

        // Set the maximum number of connections to keep alive in the connection pool
        System.setProperty("jdk.httpclient.idleTimeout", "15"); // 15 seconds
        System.setProperty("jdk.httpclient.connectionPoolSize", "256");

        // We don't want to use too much memory caching sessions for https
        System.setProperty("javax.net.ssl.sessionCacheSize", "2048");

        try {
            Injector injector = Guice.createInjector(
                    new CrawlerModule(),
                    new ProcessConfigurationModule("crawler"),
                    new DatabaseModule(false)
            );
            var crawler = injector.getInstance(CrawlerMain.class);

            var instructions = crawler.fetchInstructions(nu.marginalia.mqapi.crawling.CrawlRequest.class);
            try {
                var req = instructions.value();
                if (req.targetDomainName != null) {
                    crawler.runForSingleDomain(req.targetDomainName, req.crawlStorage);
                }
                else {
                    crawler.runForDatabaseDomains(req.crawlStorage);
                }
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

    public void runForDatabaseDomains(FileStorageId fileStorageId) throws Exception {
        runForDatabaseDomains(fileStorageService.getStorage(fileStorageId).asPath());
    }

    public void runForDatabaseDomains(Path outputDir) throws Exception {

        heartbeat.start();

        logger.info("Loading domains to be crawled");

        final List<CrawlSpecRecord> crawlSpecRecords = new ArrayList<>();
        final List<EdgeDomain> domainsToCrawl = new ArrayList<>();

        // Assign any domains with node_affinity=0 to this node, and then fetch all domains assigned to this node
        // to be crawled.

        try (var conn = dataSource.getConnection()) {
            try (var assignFreeDomains = conn.prepareStatement(
                    """
                        UPDATE EC_DOMAIN
                        SET NODE_AFFINITY=?
                        WHERE NODE_AFFINITY=0
                        """))
            {
                // Assign any domains with node_affinity=0 to this node.  We must do this now, before we start crawling
                // to avoid race conditions with other crawl runs.  We don't want multiple crawlers to crawl the same domain.
                assignFreeDomains.setInt(1, node);
                assignFreeDomains.executeUpdate();
            }

            try (var query = conn.prepareStatement("""
                     SELECT DOMAIN_NAME, COALESCE(VISITED_URLS, 0), EC_DOMAIN.ID
                     FROM EC_DOMAIN
                     LEFT JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                     WHERE NODE_AFFINITY=?
                     """)) {
                // Fetch the domains to be crawled
                query.setInt(1, node);
                query.setFetchSize(10_000);
                var rs = query.executeQuery();

                while (rs.next()) {
                    // Skip blacklisted domains
                    int domainId = rs.getInt(3);
                    if (blacklist.isBlacklisted(domainId))
                        continue;

                    int existingUrls = rs.getInt(2);
                    String domainName = rs.getString(1);

                    domainsToCrawl.add(new EdgeDomain(domainName));
                    crawlSpecRecords.add(CrawlSpecRecord.growExistingDomain(domainName, existingUrls));
                    totalTasks++;
                }
            }
        }

        logger.info("Loaded {} domains", crawlSpecRecords.size());

        crawlSpecRecords.sort(crawlSpecArrangement(crawlSpecRecords));

        // First a validation run to ensure the file is all good to parse
        if (crawlSpecRecords.isEmpty()) {
            // This is an error state, and we should make noise about it
            throw new IllegalStateException("No crawl tasks found, refusing to continue");
        }
        else {
            logger.info("Queued {} crawl tasks, let's go", crawlSpecRecords.size());
        }

        // Set up the work log and the warc archiver so we can keep track of what we've done
        try (WorkLog workLog = new WorkLog(outputDir.resolve("crawler.log"));
             DomainStateDb domainStateDb = new DomainStateDb(outputDir.resolve("domainstate.db"));
             WarcArchiverIf warcArchiver = warcArchiverFactory.get(outputDir);
             AnchorTagsSource anchorTagsSource = anchorTagsSourceFactory.create(domainsToCrawl)
        ) {
            // Set the number of tasks done to the number of tasks that are already finished,
            // (this happens when the process is restarted after a crash or a shutdown)
            tasksDone.set(workLog.countFinishedJobs());

            // List of deferred tasks used to ensure beneficial scheduling of domains with regard to DomainLocks,
            // merely shuffling the domains tends to lead to a lot of threads being blocked waiting for a semphore,
            // this will more aggressively attempt to schedule the jobs to avoid blocking
            List<CrawlTask> taskList = new ArrayList<>();

            // Create crawl tasks
            for (CrawlSpecRecord crawlSpec : crawlSpecRecords) {
                if (workLog.isJobFinished(crawlSpec.domain))
                    continue;

                var task = new CrawlTask(
                        crawlSpec,
                        anchorTagsSource,
                        outputDir,
                        warcArchiver,
                        domainStateDb,
                        workLog);

                // Try to run immediately, to avoid unnecessarily keeping the entire work set in RAM
                if (!trySubmitDeferredTask(task)) {
                    // Otherwise add to the taskList for deferred execution
                    taskList.add(task);
                }
            }

             // Schedule viable tasks for execution until list is empty
            for (int emptyRuns = 0;emptyRuns < 300;) {
                boolean hasTasks = !taskList.isEmpty();

                // The order of these checks  very important to avoid a race condition
                // where we miss a task that is put into the retry queue
                boolean hasRunningTasks = pool.getActiveCount() > 0;
                boolean hasRetryTasks = !retryQueue.isEmpty();

                if (hasTasks || hasRetryTasks || hasRunningTasks) {
                    retryQueue.drainTo(taskList);

                    // Try to submit any tasks that are in the retry queue (this will block if the pool is full)
                    taskList.removeIf(this::trySubmitDeferredTask);

                    // Add a small pause here to avoid busy looping toward the end of the execution cycle when
                    // we might have no new viable tasks to run for hours on end
                    TimeUnit.MILLISECONDS.sleep(5);
                } else {
                    // We have no tasks to run, and no tasks in the retry queue
                    // but we wait a bit to see if any new tasks come in via the retry queue
                    emptyRuns++;
                    TimeUnit.SECONDS.sleep(1);
                }
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

    /** Create a comparator that sorts the crawl specs in a way that is beneficial for the crawl,
     * we want to enqueue domains that have common top domains first, but otherwise have a random
     * order.
     * <p></p>
     * Note, we can't use hash codes for randomization as it is not desirable to have the same order
     * every time the process is restarted (and CrawlSpecRecord is a record, which defines equals and
     * hashcode based on the fields).
     * */
    private Comparator<CrawlSpecRecord> crawlSpecArrangement(List<CrawlSpecRecord> records) {
        Random r = new Random();
        Map<String, Integer> topDomainCounts = new HashMap<>(4 + (int) Math.sqrt(records.size()));
        Map<String, Integer> randomOrder = new HashMap<>(records.size());

        for (var spec : records) {
            topDomainCounts.merge(EdgeDomain.getTopDomain(spec.domain), 1, Integer::sum);
            randomOrder.put(spec.domain, r.nextInt());
        }

        return Comparator.comparing((CrawlSpecRecord spec) -> topDomainCounts.getOrDefault(EdgeDomain.getTopDomain(spec.domain), 0) >= 8)
                .reversed()
                .thenComparing(spec -> randomOrder.get(spec.domain))
                .thenComparing(Record::hashCode); // non-deterministic tie-breaker to
    }

    /** Submit a task for execution if it can be run, returns true if it was submitted
     * or if it can be discarded */
    private boolean trySubmitDeferredTask(CrawlTask task) {
        if (!task.canRun()) {
            return false;
        }

        if (pendingCrawlTasks.putIfAbsent(task.domain, task) != null) {
            return true; // task has already run, duplicate in crawl specs
        }

        try {
            // This blocks the caller when the pool is full
            pool.submitQuietly(task);
            return true;
        }
        catch (RuntimeException ex) {
            logger.error("Failed to submit task " + task.domain, ex);
            return false;
        }
    }

    public void runForSingleDomain(String targetDomainName, FileStorageId fileStorageId) throws Exception {
        runForSingleDomain(targetDomainName, fileStorageService.getStorage(fileStorageId).asPath());
    }

    public void runForSingleDomain(String targetDomainName, Path outputDir) throws Exception {

        heartbeat.start();

        try (WorkLog workLog = new WorkLog(outputDir.resolve("crawler-" + targetDomainName.replace('/', '-') + ".log"));
             DomainStateDb domainStateDb = new DomainStateDb(outputDir.resolve("domainstate.db"));
             WarcArchiverIf warcArchiver = warcArchiverFactory.get(outputDir);
             AnchorTagsSource anchorTagsSource = anchorTagsSourceFactory.create(List.of(new EdgeDomain(targetDomainName)))
        ) {
            var spec = new CrawlSpecRecord(targetDomainName, 1000, List.of());
            var task = new CrawlTask(spec, anchorTagsSource, outputDir, warcArchiver, domainStateDb, workLog);
            task.run();
        }
        catch (Exception ex) {
            logger.warn("Exception in crawler", ex);
        }
        finally {
            heartbeat.shutDown();
        }
    }

    private class CrawlTask implements SimpleBlockingThreadPool.Task {

        private final CrawlSpecRecord specification;

        private final String domain;
        private final String id;

        private final AnchorTagsSource anchorTagsSource;
        private final Path outputDir;
        private final WarcArchiverIf warcArchiver;
        private final DomainStateDb domainStateDb;
        private final WorkLog workLog;

        CrawlTask(CrawlSpecRecord specification,
                  AnchorTagsSource anchorTagsSource,
                  Path outputDir,
                  WarcArchiverIf warcArchiver,
                  DomainStateDb domainStateDb,
                  WorkLog workLog)
        {
            this.specification = specification;
            this.anchorTagsSource = anchorTagsSource;
            this.outputDir = outputDir;
            this.warcArchiver = warcArchiver;
            this.domainStateDb = domainStateDb;
            this.workLog = workLog;

            this.domain = specification.domain();
            this.id = Integer.toHexString(domain.hashCode());
        }

        /** Best effort indicator whether we could start this now without getting stuck in
         * DomainLocks purgatory */
        public boolean canRun() {
            return domainLocks.isLockableHint(new EdgeDomain(domain));
        }

        @Override
        public void run() throws Exception {

            if (workLog.isJobFinished(domain)) { // No-Op
                logger.info("Omitting task {}, as it is already run", domain);
                return;
            }

            Optional<DomainLocks.DomainLock> lock = domainLocks.tryLockDomain(new EdgeDomain(domain));
            // We don't have a lock, so we can't run this task
            // we return to avoid blocking the pool for too long
            if (lock.isEmpty()) {
                if (retryQueue.remainingCapacity() > 0) {
                    // Sleep a moment to avoid busy looping via the retry queue
                    // in the case when few tasks remain and almost all are ineligible for
                    // immediate restart
                    Thread.sleep(5);
                }

                retryQueue.put(this);
                return;
            }
            DomainLocks.DomainLock domainLock = lock.get();

            try (domainLock) {
                Thread.currentThread().setName("crawling:" + domain);

                Path newWarcFile = CrawlerOutputFile.createWarcPath(outputDir, id, domain, CrawlerOutputFile.WarcFileVersion.LIVE);
                Path tempFile = CrawlerOutputFile.createWarcPath(outputDir, id, domain, CrawlerOutputFile.WarcFileVersion.TEMP);
                Path slopFile = CrawlerOutputFile.createSlopPath(outputDir, id, domain);

                // Move the WARC file to a temp file if it exists, so we can resume the crawl using the old data
                // while writing to the same file name as before
                if (Files.exists(newWarcFile)) {
                    Files.move(newWarcFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                else {
                    Files.deleteIfExists(tempFile);
                }

                try (var warcRecorder = new WarcRecorder(newWarcFile); // write to a temp file for now
                     var retriever = new CrawlerRetreiver(fetcher, domainProber, specification, domainStateDb, warcRecorder);
                     CrawlDataReference reference = getReference())
                {
                    // Resume the crawl if it was aborted
                    if (Files.exists(tempFile)) {
                        retriever.syncAbortedRun(tempFile);
                        Files.delete(tempFile);
                    }

                    DomainLinks domainLinks = anchorTagsSource.getAnchorTags(domain);

                    int size = retriever.crawlDomain(domainLinks, reference);

                    // Delete the reference crawl data if it's not the same as the new one
                    // (mostly a case when migrating from legacy->warc)
                    reference.delete();

                    // Convert the WARC file to Slop
                    SlopCrawlDataRecord
                            .convertWarc(domain, userAgent, newWarcFile, slopFile);

                    // Optionally archive the WARC file if full retention is enabled,
                    // otherwise delete it:
                    warcArchiver.consumeWarc(newWarcFile, domain);

                    // Mark the domain as finished in the work log
                    workLog.setJobToFinished(domain, slopFile.toString(), size);

                    // Update the progress bar
                    heartbeat.setProgress(tasksDone.incrementAndGet() / (double) totalTasks);

                    logger.info("Fetched {}", domain);
                } catch (Exception e) {
                    logger.error("Error fetching domain " + domain, e);
                }
                finally {
                    // We don't need to double-count these; it's also kept in the workLog
                    pendingCrawlTasks.remove(domain);
                    Thread.currentThread().setName("[idle]");

                    Files.deleteIfExists(newWarcFile);
                    Files.deleteIfExists(tempFile);
                }
            }
        }

        private CrawlDataReference getReference() {
            try {
                Path slopPath = CrawlerOutputFile.getSlopPath(outputDir, id, domain);
                if (Files.exists(slopPath)) {
                    return new CrawlDataReference(slopPath);
                }

                Path parquetPath = CrawlerOutputFile.getParquetPath(outputDir, id, domain);
                if (Files.exists(parquetPath)) {
                    slopPath = migrateParquetData(parquetPath, domain, outputDir);
                    return new CrawlDataReference(slopPath);
                }

            } catch (Exception e) {
                logger.debug("Failed to read previous crawl data for {}", specification.domain());
            }

            return new CrawlDataReference();
        }

    }

    public record CrawlSpecRecord(@NotNull String domain, int crawlDepth, @NotNull List<String> urls) {

        public CrawlSpecRecord(String domain, int crawlDepth) {
            this(domain, crawlDepth, List.of());
        }

        public static CrawlSpecRecord growExistingDomain(String domain, int visitedUrls) {
            // Calculate the number of URLs to fetch for this domain, based on the number of URLs
            // already fetched, and a growth factor that gets a bonus for small domains
            return new CrawlSpecRecord(domain,
                    (int) Math.clamp(
                            (visitedUrls * (visitedUrls < MID_URLS_PER_DOMAIN
                                    ? Math.max(2.5, URL_GROWTH_FACTOR)
                                    : URL_GROWTH_FACTOR)
                            ),
                            MIN_URLS_PER_DOMAIN,
                            MAX_URLS_PER_DOMAIN));
        }

        public static CrawlSpecRecordBuilder builder() {
            return new CrawlSpecRecordBuilder();
        }

        public static class CrawlSpecRecordBuilder {
            private @NotNull String domain;
            private int crawlDepth;
            private @NotNull List<String> urls;

            CrawlSpecRecordBuilder() {
            }

            public CrawlSpecRecordBuilder domain(@NotNull String domain) {
                this.domain = domain;
                return this;
            }

            public CrawlSpecRecordBuilder crawlDepth(int crawlDepth) {
                this.crawlDepth = crawlDepth;
                return this;
            }

            public CrawlSpecRecordBuilder urls(@NotNull List<String> urls) {
                this.urls = urls;
                return this;
            }

            public CrawlSpecRecord build() {
                return new CrawlSpecRecord(this.domain, this.crawlDepth, this.urls);
            }

            public String toString() {
                return "CrawlerMain.CrawlSpecRecord.CrawlSpecRecordBuilder(domain=" + this.domain + ", crawlDepth=" + this.crawlDepth + ", urls=" + this.urls + ")";
            }
        }
    }

    // Migrate from parquet to slop if necessary
    //
    // This must be synchronized as chewing through parquet files in parallel leads to enormous memory overhead
    private synchronized Path migrateParquetData(Path inputPath, String domain, Path crawlDataRoot) throws IOException {
        if (!inputPath.toString().endsWith(".parquet")) {
            return inputPath;
        }

        Path outputFile = CrawlerOutputFile.createSlopPath(crawlDataRoot, Integer.toHexString(domain.hashCode()), domain);

        SlopCrawlDataRecord.convertFromParquet(inputPath, outputFile);

        return outputFile;
    }

}
