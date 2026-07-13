package nu.marginalia.crawl;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.coordination.DomainLock;
import nu.marginalia.crawl.fetcher.CrawlerAuditLog;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainProber;
import nu.marginalia.crawl.warc.WarcArchiverFactory;
import nu.marginalia.crawl.warc.WarcArchiverIf;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.io.CrawlerOutputFile;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessEventLog;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static nu.marginalia.mqapi.ProcessInboxNames.CRAWLER_INBOX;
import static nu.marginalia.slop.SlopCrawlDataRecord.convertWarc;

public class CrawlerMain extends ProcessMainClass {
    private final static Logger logger = LoggerFactory.getLogger(CrawlerMain.class);

    private final UserAgent userAgent;
    private final ProcessHeartbeatImpl heartbeat;
    private final ProcessEventLog eventLog;
    private final DomainProber domainProber;
    private final FileStorageService fileStorageService;
    private final AnchorTagsSourceFactory anchorTagsSourceFactory;
    private final WarcArchiverFactory warcArchiverFactory;
    private final HikariDataSource dataSource;
    private final DomainBlacklist blacklist;
    private final NodeConfigurationService nodeConfigurationService;
    private final int node;
    private final ServiceRegistryIf serviceRegistry;
    private final SimpleBlockingThreadPool pool;

    private final DomainCoordinator domainCoordinator;

    private final Map<EdgeDomain, DomainAvailability> availabilityData = new HashMap<>();

    private final Map<String, CrawlTask> pendingCrawlTasks = new ConcurrentHashMap<>();

    private final LinkedBlockingQueue<CrawlTask> retryQueue = new LinkedBlockingQueue<>();

    private final AtomicInteger tasksDone = new AtomicInteger(0);
    private final HttpFetcherImpl fetcher;
    private final CrawlerAuditLog auditLog;

    private int totalTasks = 1;

    private static final double URL_GROWTH_FACTOR = Double.parseDouble(System.getProperty("crawler.crawlSetGrowthFactor", "1.25"));
    private static final int MIN_URLS_PER_DOMAIN = Integer.getInteger("crawler.minUrlsPerDomain", 100);
    private static final int MID_URLS_PER_DOMAIN = Integer.getInteger("crawler.midUrlsPerDomain", 2_000);
    private static final int MAX_URLS_PER_DOMAIN = Integer.getInteger("crawler.maxUrlsPerDomain", 10_000);

    @Inject
    public CrawlerMain(UserAgent userAgent,
                       HttpFetcherImpl httpFetcher,
                       CrawlerAuditLog auditLog,
                       ProcessHeartbeatImpl heartbeat,
                       ProcessEventLog eventLog,
                       MessageQueueFactory messageQueueFactory, DomainProber domainProber,
                       FileStorageService fileStorageService,
                       ProcessConfiguration processConfiguration,
                       AnchorTagsSourceFactory anchorTagsSourceFactory,
                       WarcArchiverFactory warcArchiverFactory,
                       HikariDataSource dataSource,
                       DomainBlacklist blacklist,
                       NodeConfigurationService nodeConfigurationService,
                       DomainCoordinator domainCoordinator,
                       ServiceRegistryIf serviceRegistry,
                       Gson gson) throws InterruptedException {

        super(messageQueueFactory, processConfiguration, gson, CRAWLER_INBOX);

        this.userAgent = userAgent;
        this.fetcher = httpFetcher;
        this.auditLog = auditLog;
        this.heartbeat = heartbeat;
        this.eventLog = eventLog;
        this.domainProber = domainProber;
        this.fileStorageService = fileStorageService;
        this.anchorTagsSourceFactory = anchorTagsSourceFactory;
        this.warcArchiverFactory = warcArchiverFactory;
        this.dataSource = dataSource;
        this.blacklist = blacklist;
        this.nodeConfigurationService = nodeConfigurationService;
        this.node = processConfiguration.node();
        this.serviceRegistry = serviceRegistry;
        this.domainCoordinator = domainCoordinator;

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
        System.setProperty("sun.net.client.defaultConnectTimeout", 
                System.getProperty("crawler.jvmConnectTimeout", "30000"));
        System.setProperty("sun.net.client.defaultReadTimeout", 
                System.getProperty("crawler.jvmReadTimeout", "30000"));

        // Set the maximum number of connections to keep alive in the connection pool
        System.setProperty("jdk.httpclient.idleTimeout", 
                System.getProperty("crawler.httpClientIdleTimeout", "15")); // 15 seconds
        System.setProperty("jdk.httpclient.connectionPoolSize", 
                System.getProperty("crawler.httpClientConnectionPoolSize", "256"));

        // We don't want to use too much memory caching sessions for https
        System.setProperty("javax.net.ssl.sessionCacheSize", "2048");

        try {
            Injector injector = Guice.createInjector(
                    new CrawlerModule(),
                    new ProcessConfigurationModule("crawler"),
                    new ServiceDiscoveryModule(),
                    new DatabaseModule(false)
            );
            var crawler = injector.getInstance(CrawlerMain.class);

            var instructions = crawler.fetchInstructions(nu.marginalia.mqapi.crawling.CrawlRequest.class);

            crawler.serviceRegistry.registerProcess("crawler", crawler.node);

            try {
                crawler.eventLog.logEvent("CRAWLER-INFO", "Crawling started");
                var req = instructions.value();
                if (req.targetDomainName != null) {
                    crawler.runForSingleDomain(req.targetDomainName, req.crawlStorage);
                }
                else {
                    crawler.runForDatabaseDomains(req.crawlStorage);
                }
                crawler.eventLog.logEvent("CRAWLER-INFO", "Crawl completed successfully");
                instructions.ok();
            } catch (Exception ex) {
                logger.error("Crawler failed", ex);
                instructions.err();
            }
            finally {
                crawler.auditLog.close();
                crawler.serviceRegistry.deregisterProcess("crawler", crawler.node);
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

        NodeProfile profile = nodeConfigurationService.get(node).profile();
        RunType runType = RunType.forProfile(profile);

        DomainsToCrawl work = loadDomainsToCrawl(profile);

        if (work.specs().isEmpty()) {
            // This is an error state, and we should make noise about it
            throw new IllegalStateException("No crawl tasks found, refusing to continue");
        }
        logger.info("Queued {} crawl tasks, let's go", work.specs().size());

        try {
            crawl(outputDir, work, runType);
        }
        catch (Exception ex) {
            logger.warn("Exception in crawler", ex);
        }
        finally {
            heartbeat.shutDown();
        }
    }

    /** Load the domains assigned to this node that should be crawled, dropping blacklisted and
     * unreachable ones.
     */
    private DomainsToCrawl loadDomainsToCrawl(NodeProfile profile) throws SQLException {
        logger.info("Loading domains to be crawled");

        List<CrawlSpecRecord> crawlSpecRecords = new ArrayList<>();
        List<EdgeDomain> domainsToCrawl = new ArrayList<>();

        try (var conn = dataSource.getConnection()) {
            // Claim unassigned domains for this node now, before crawling, so that concurrent crawl runs
            // don't race to crawl the same domain.
            if (profile.isWideDomains()) {
                try (var stmt = conn.prepareStatement("""
                        UPDATE EC_DOMAIN SET NODE_AFFINITY=?
                        WHERE NODE_AFFINITY=0 AND DOMAIN_TOP IN (SELECT DOMAIN_TOP FROM WIDE_DOMAIN_ROOTS)
                        """))
                {
                    stmt.setInt(1, node);
                    stmt.executeUpdate();
                }
            }
            else {
                try (var stmt = conn.prepareStatement("""
                        UPDATE EC_DOMAIN SET NODE_AFFINITY=?
                        WHERE NODE_AFFINITY=0 AND DOMAIN_TOP NOT IN (SELECT DOMAIN_TOP FROM WIDE_DOMAIN_ROOTS)
                        """))
                {
                    stmt.setInt(1, node);
                    stmt.executeUpdate();
                }
            }

            IntArrayList domainIds = new IntArrayList(100_000);

            try (var query = conn.prepareStatement("""
                     SELECT DOMAIN_NAME, COALESCE(VISITED_URLS, 0), EC_DOMAIN.ID
                     FROM EC_DOMAIN
                     LEFT JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                     WHERE NODE_AFFINITY=?
                     """)) {
                query.setInt(1, node);
                query.setFetchSize(10_000);
                var rs = query.executeQuery();

                while (rs.next()) {
                    int domainId = rs.getInt(3);
                    if (blacklist.isBlacklisted(domainId))
                        continue;
                    domainIds.add(domainId);

                    int existingUrls = rs.getInt(2);
                    String domainName = rs.getString(1);

                    domainsToCrawl.add(new EdgeDomain(domainName));
                    crawlSpecRecords.add(CrawlSpecRecord.growExistingDomain(domainName, existingUrls));
                    totalTasks++;
                }
            }

            logger.info("Loaded {} domains", crawlSpecRecords.size());

            fetchAvailability(conn, domainIds);
        }

        // Remove crawl tasks for domains we haven't seen in a long time
        int sizeOriginal = domainsToCrawl.size();
        domainsToCrawl.removeIf(domain -> availabilityData.get(domain) == DomainAvailability.MISSING);
        crawlSpecRecords.removeIf(spec -> availabilityData.get(new EdgeDomain(spec.domain)) == DomainAvailability.MISSING);
        if (domainsToCrawl.size() != sizeOriginal) {
            logger.info("Removed {} crawl tasks for unreachable domains", (sizeOriginal - domainsToCrawl.size()));
        }

        return new DomainsToCrawl(domainsToCrawl, crawlSpecRecords);
    }

    /** Populate {@link #availabilityData} for the given domain ids from the ping subsystem, so that
     * unreachable domains can be dropped from the crawl.
     */
    private void fetchAvailability(Connection conn, IntArrayList domainIds) throws SQLException {
        try (var ps = conn.prepareStatement("""
            SELECT DOMAIN_NAME, SERVER_AVAILABLE, TS_LAST_PING, TS_LAST_AVAILABLE, TS_LAST_ERROR
            FROM DOMAIN_AVAILABILITY_INFORMATION
            INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID
            WHERE DOMAIN_ID = ?
                """)
        ) {
            Instant now = Instant.now();

            for (int id : domainIds) {
                ps.setInt(1, id);
                var rs = ps.executeQuery();

                if (rs.next()) {
                    String domainName = rs.getString("DOMAIN_NAME");
                    boolean serverAvailable = rs.getBoolean("SERVER_AVAILABLE");

                    Instant tsLastPing = Optional.ofNullable(rs.getTimestamp("TS_LAST_PING"))
                            .map(Timestamp::toInstant)
                            .orElse(Instant.EPOCH);
                    Instant tsLastAvailable = Optional.ofNullable(rs.getTimestamp("TS_LAST_AVAILABLE"))
                            .map(Timestamp::toInstant)
                            .orElse(Instant.EPOCH);
                    Instant tsLastError = Optional.ofNullable(rs.getTimestamp("TS_LAST_ERROR"))
                            .map(Timestamp::toInstant)
                            .orElse(Instant.EPOCH);

                    if (tsLastPing.isBefore(now.minus(Duration.ofDays(3)))) {
                        continue; // data is stale, nothing can be said
                    }

                    boolean recentError = tsLastError.isAfter(now.minus(Duration.ofDays(7)));
                    boolean recentAvailable = tsLastAvailable.isAfter(now.minus(Duration.ofDays(7)));

                    if (serverAvailable) {
                        availabilityData.put(new EdgeDomain(domainName), DomainAvailability.REACHABLE);
                    } else if (recentError && recentAvailable) {
                        availabilityData.put(new EdgeDomain(domainName), DomainAvailability.FLAKEY);
                    } else {
                        availabilityData.put(new EdgeDomain(domainName), DomainAvailability.MISSING);
                    }
                }
            }
        }

        logger.info("Fetched availability data");
    }

    /** Run the loaded domains through the crawl pool.
     */
    private void crawl(Path outputDir, DomainsToCrawl work, RunType runType) throws Exception {
        List<CrawlSpecRecord> specs = work.specs();

        try (WorkLog workLog = new WorkLog(outputDir.resolve("crawler.log"));
             DomainStateDb domainStateDb = new DomainStateDb(outputDir.resolve("domainstate.db"));
             WarcArchiverIf warcArchiver = warcArchiverFactory.get(outputDir);
             AnchorTagsSource anchorTagsSource = anchorTagsSourceFactory.create(work.domains())
        ) {

            specs.sort(
                    switch(runType) {
                        case BatchRun() -> crawlSpecArrangement(specs);
                        case TimedRun(_,_) -> leastRecentlyCrawledFirst(domainStateDb.getLastFullCrawlTimes());
                    }
            );

            // A partial pass recrawls everything it reaches, so it starts its progress count from zero
            // rather than resuming from the work log.
            if (runType.isWorkLogDriven()) {
                tasksDone.set(workLog.countFinishedJobs());
            }

            // List of deferred tasks used to ensure beneficial scheduling of domains with regard to DomainLocks,
            // merely shuffling the domains tends to lead to a lot of threads being blocked waiting for a semaphore,
            // this will more aggressively attempt to schedule the jobs to avoid blocking
            List<CrawlTask> taskList = new ArrayList<>();

            for (CrawlSpecRecord crawlSpec : specs) {
                if (runType.isPastDeadline())
                    break;

                // A partial pass does not rotate the work log, so it must recrawl domains a previous
                // run already finished rather than skip them.
                if (runType.isWorkLogDriven() && workLog.isJobFinished(crawlSpec.domain))
                    continue;

                var task = new CrawlTask(crawlSpec, anchorTagsSource, outputDir, warcArchiver, domainStateDb, workLog, runType);

                // Try to run immediately, to avoid unnecessarily keeping the entire work set in RAM
                if (!trySubmitDeferredTask(task)) {
                    // Drain the retry queue to the taskList, and try to submit any tasks that are in the retry queue
                    retryQueue.drainTo(taskList);
                    taskList.removeIf(this::trySubmitDeferredTask);
                    // Then add this new task to the retry queue
                    taskList.add(task);
                }
            }

            // Schedule viable tasks for execution until the list is empty or the deadline passes
            for (int emptyRuns = 0; emptyRuns < 300;) {
                if (runType.isPastDeadline())
                    break;

                boolean hasTasks = !taskList.isEmpty();

                // The order of these checks is very important to avoid a race condition
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

            awaitCrawlCompletion();
        }

        if (!runType.isWorkLogDriven()) {
            // Ensure the crawler.log has a sane shape for downstream consumers of crawl data
            // even if the run itself doesn't rely on it
            compactCrawlerLog(outputDir.resolve("crawler.log"));
        }
    }

    /** Rewrite the crawler.log so it holds a single (latest) entry per domain, replacing the file
     * atomically.  The work log must already be closed when this runs.
     */
    public static void compactCrawlerLog(Path logPath) throws IOException {
        if (!Files.exists(logPath)) {
            return;
        }

        Map<String, WorkLogEntry> latestByDomain = new LinkedHashMap<>();
        for (var entry : WorkLog.iterable(logPath)) {
            latestByDomain.put(entry.id(), entry);
        }

        Path tempLog = Files.createTempFile(logPath.getParent(), "crawler", ".log");
        try (WorkLog compacted = new WorkLog(tempLog)) {
            for (var entry : latestByDomain.values()) {
                compacted.setJobToFinished(entry.id(), entry.path(), entry.cnt());
            }
        }
        Files.move(tempLog, logPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Stop accepting new work and wait for the ongoing crawls to finish, aborting only if they stall. */
    private void awaitCrawlCompletion() throws InterruptedException {
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

    /** The set of domains selected for a crawl: the {@code EdgeDomain} list feeds the anchor-tags
     * source, the {@code CrawlSpecRecord} list drives the crawl itself. */
    private record DomainsToCrawl(List<EdgeDomain> domains, List<CrawlSpecRecord> specs) {}

    public static Comparator<CrawlSpecRecord> leastRecentlyCrawledFirst(Map<String, Long> lastCrawlTimesMs) {
        return Comparator
                .comparingLong((CrawlSpecRecord spec) -> lastCrawlTimesMs.getOrDefault(spec.domain(), 0L))
                .thenComparing(CrawlSpecRecord::domain);
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
            var task = new CrawlTask(spec, anchorTagsSource, outputDir, warcArchiver, domainStateDb, workLog, new BatchRun());
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
        private final RunType runType;

        CrawlTask(CrawlSpecRecord specification,
                  AnchorTagsSource anchorTagsSource,
                  Path outputDir,
                  WarcArchiverIf warcArchiver,
                  DomainStateDb domainStateDb,
                  WorkLog workLog,
                  RunType runType)
        {
            this.specification = specification;
            this.anchorTagsSource = anchorTagsSource;
            this.outputDir = outputDir;
            this.warcArchiver = warcArchiver;
            this.domainStateDb = domainStateDb;
            this.workLog = workLog;
            this.runType = runType;

            this.domain = specification.domain();
            this.id = Integer.toHexString(domain.hashCode());
        }

        /** Best effort indicator whether we could start this now without getting stuck in
         * DomainLocks purgatory */
        public boolean canRun() {
            return domainCoordinator.isLockableHint(new EdgeDomain(domain));
        }

        @Override
        public void run() throws Exception {

            if (isJobFinished()) { // No-Op
                logger.info("Omitting task {}, as it is already run", domain);
                pendingCrawlTasks.remove(domain);
                return;
            }

            Optional<DomainLock> lock = domainCoordinator.tryLockDomain(new EdgeDomain(domain), Duration.ofSeconds(2));
            // We don't have a lock, so we can't run this task
            // we return to avoid blocking the pool for too long
            if (lock.isEmpty()) {
                pendingCrawlTasks.remove(domain);
                retryQueue.put(this);
                return;
            }
            DomainLock domainLock = lock.get();

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

                    DomainAvailability availability = availabilityData.getOrDefault(new EdgeDomain(domain), DomainAvailability.DATA_MISSING);

                    final boolean domainRecentlyAvailable =  availability == DomainAvailability.REACHABLE
                                                          || availability == DomainAvailability.FLAKEY;

                    final boolean hasOldSlopFile = Files.exists(slopFile);

                    switch (retriever.crawlDomain(domainLinks, reference)) {

                        // Success case
                        case CrawlerRetreiver.CrawlerResult.Crawled(int size) -> {
                            reference.delete();
                            convertWarc(domain, userAgent, newWarcFile, slopFile);
                            workLog.setJobToFinished(domain, slopFile.toString(), size);
                        }

                        // Non-Error cases where we have no crawl data

                        case CrawlerRetreiver.CrawlerResult.Blocked() -> {
                            reference.delete();
                            workLog.setJobToFinished(domain, slopFile.toString(), 0, "Blocked");
                        }

                        case CrawlerRetreiver.CrawlerResult.Redirect() -> {
                            reference.delete();
                            workLog.setJobToFinished(domain, slopFile.toString(), 0, "Redirect");
                        }

                        // Error, but the site was seen recently
                        case CrawlerRetreiver.CrawlerResult.Error(String why)
                                when hasOldSlopFile && domainRecentlyAvailable -> {
                            // Retain existing crawl data since the error is new, possibly transient
                            workLog.setJobToFinished(domain, slopFile.toString(), 0, availability.name() + ": " + why);
                        }

                        // Error, but we haven't seen the site recently
                        case CrawlerRetreiver.CrawlerResult.Error(String why) -> {
                            reference.delete();
                            workLog.setJobToFinished(domain, slopFile.toString(), 0, availability.name() + ": " + why);
                        }
                    }

                    // Optionally archive the WARC file if full retention is enabled,
                    // otherwise delete it:
                    warcArchiver.consumeWarc(newWarcFile, domain);

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

        private boolean isJobFinished() {
            if (!runType.isWorkLogDriven())
                return false;

            // Full batch passes use the work log instead
            return workLog.isJobFinished(domain);
        }

        private CrawlDataReference getReference() {
            try {
                Path slopPath = CrawlerOutputFile.getSlopPath(outputDir, id, domain);
                if (Files.exists(slopPath)) {
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


}

enum DomainAvailability {
    DATA_MISSING,
    REACHABLE,
    FLAKEY,
    MISSING
}


sealed interface RunType permits BatchRun, TimedRun {
    static RunType forProfile(NodeProfile nodeProfile) {
        if (nodeProfile.isWideDomains()) {
            return new TimedRun(Optional.ofNullable(Integer.getInteger("crawler.maxRunTimeSeconds"))
                    .map(Duration::ofSeconds)
                    .orElse(Duration.ofDays(7)));
        }
        else if (nodeProfile.isBatchCrawl()) {
            return new BatchRun();
        }
        else {
            throw new IllegalArgumentException("Nodes of type " + nodeProfile + " should not be running a crawler");
        }
    }

    boolean isPastDeadline();

    /** Should the WorkLog be an authority on whether a task is completed? */
    boolean isWorkLogDriven();
}

record BatchRun() implements RunType {
    public boolean isPastDeadline() {
        return false;
    }

    public boolean isWorkLogDriven() {
        return true;
    }
}

record TimedRun(Duration runTime, Instant deadline) implements RunType {
    public TimedRun(Duration runTime) {
        this(runTime, Instant.now().plus(runTime));
    }

    @Override
    public boolean isPastDeadline() {
        return Instant.now().isAfter(deadline);
    }

    // Timed runs can not be driven by the crawler.log, and instead use domainstatedb timings to ensure a crawl order
    // where we don't repeat work
    public boolean isWorkLogDriven() {
        return false;
    }

}
