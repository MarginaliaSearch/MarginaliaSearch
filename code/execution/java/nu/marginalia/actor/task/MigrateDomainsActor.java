package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.crawl.DomainStateDb;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Migrates the crawl data of hand picked domains from whatever node currently owns them onto
 * this node, and takes ownership by setting EC_DOMAIN.NODE_AFFINITY.  The set of domains to move
 * is read from the DOMAIN_MIGRATION_QUEUE table.
 */
@Singleton
public class MigrateDomainsActor extends RecordActorPrototype {

    private final FileStorageService storageService;
    private final HikariDataSource dataSource;
    private final ExecutorClient executorClient;
    private final ServiceEventLog eventLog;
    private final ServiceHeartbeat heartbeat;
    private final int nodeId;

    @Resume(behavior = ActorResumeBehavior.ERROR)
    public record Initial() implements ActorStep {}

    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Migrate(FileStorageId localStorageId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> {
                expandWideDomainRoots();

                var existing = storageService.getOnlyActiveFileStorage(FileStorageType.CRAWL_DATA);
                FileStorageId localStorageId = existing.orElseGet(this::allocateCrawlStorage);
                yield new Migrate(localStorageId);
            }
            case Migrate(FileStorageId localStorageId) -> {
                Map<Integer, List<DomainToMigrate>> domainsBySource = loadQueue();
                if (domainsBySource.isEmpty()) {
                    yield new End();
                }

                Path localBase = storageService.getStorage(localStorageId).asPath();
                int total = domainsBySource.values().stream().mapToInt(List::size).sum();
                int done = 0;

                try (HttpClient httpClient = HttpClient.newHttpClient();
                     WorkLog workLog = new WorkLog(localBase.resolve("crawler.log"));
                     DomainStateDb localStateDb = new DomainStateDb(localBase.resolve("domainstate.db"));
                     var hb = heartbeat.createServiceAdHocTaskHeartbeat("Migrating domains")) {

                    for (var bySource : domainsBySource.entrySet()) {
                        int sourceNode = bySource.getKey();
                        List<DomainToMigrate> domains = bySource.getValue();

                        FileStorage sourceStorage = resolveSourceStorage(sourceNode);
                        Map<String, WorkLogEntry> sourceLog = sourceStorage == null
                                ? Map.of()
                                : downloadCrawlerLog(httpClient, sourceStorage);
                        Path sourceStateDbFile = sourceStorage == null
                                ? null
                                : downloadDomainStateDb(httpClient, sourceStorage);

                        try (DomainStateDb sourceStateDb = sourceStateDbFile == null ? null : new DomainStateDb(sourceStateDbFile)) {
                            for (var domain : domains) {
                                hb.progress("migrate", done++, total);

                                WorkLogEntry logEntry = sourceLog.get(domain.name());
                                if (sourceStorage != null && logEntry != null) {
                                    Path localSlop = localBase.resolve(logEntry.relPath());
                                    if (copyRemoteFile(httpClient, sourceStorage, logEntry.relPath(), localSlop)) {
                                        workLog.setJobToFinished(domain.name(), localSlop.toString(), logEntry.cnt());
                                    }
                                    else {
                                        eventLog.logEvent(getClass().getSimpleName(),
                                                "No crawl data for " + domain.name() + " on node " + sourceNode + ", adopting without data");
                                    }
                                }

                                if (sourceStateDb != null) {
                                    carryOverDomainState(sourceStateDb, localStateDb, domain.name());
                                }

                                takeOwnership(domain.id());
                            }
                        }
                        finally {
                            if (sourceStateDbFile != null) {
                                Files.deleteIfExists(sourceStateDbFile);
                                Files.deleteIfExists(Path.of(sourceStateDbFile + "-wal"));
                                Files.deleteIfExists(Path.of(sourceStateDbFile + "-shm"));
                            }
                        }
                    }
                }

                eventLog.logEvent(getClass().getSimpleName(), "Migrated " + total + " domains to node " + nodeId);
                yield new End();
            }
            default -> new Error();
        };
    }

    private FileStorageId allocateCrawlStorage() {
        try {
            return storageService.allocateStorage(FileStorageType.CRAWL_DATA, "crawl-data", "Crawl data").id();
        }
        catch (SQLException | IOException ex) {
            throw new RuntimeException("Failed to allocate crawl data storage", ex);
        }
    }

    /** Return the active CRAWL_DATA storage of a remote source node, or null when the domains
     * on that node should be adopted without copying any data.
     */
    private FileStorage resolveSourceStorage(int sourceNode) throws SQLException {
        if (sourceNode <= 0 || sourceNode == nodeId) {
            return null;
        }
        var sourceStorageId = storageService.getOnlyActiveFileStorage(sourceNode, FileStorageType.CRAWL_DATA);
        return sourceStorageId.map(id -> {
            try {
                return storageService.getStorage(id);
            }
            catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }).orElse(null);
    }

    /** Enqueue every unclaimed subdomain of a flagged wide-domain root for migration onto this node.
     */
    private void expandWideDomainRoots() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    INSERT INTO DOMAIN_MIGRATION_QUEUE (DOMAIN_ID, DEST_NODE)
                    SELECT EC_DOMAIN.ID, ?
                    FROM EC_DOMAIN
                    JOIN WIDE_DOMAIN_ROOTS ON EC_DOMAIN.DOMAIN_TOP = WIDE_DOMAIN_ROOTS.DOMAIN_TOP
                    WHERE EC_DOMAIN.NODE_AFFINITY <> ? 
                    AND EC_DOMAIN.NODE_AFFINITY>=0
                    ON DUPLICATE KEY UPDATE DEST_NODE = VALUES(DEST_NODE)
                    """))
        {
            stmt.setInt(1, nodeId);
            stmt.setInt(2, nodeId);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                eventLog.logEvent(getClass().getSimpleName(), "Enqueued " + affected + " wide-domain subdomains for migration");
            }
        }
    }

    private Map<Integer, List<DomainToMigrate>> loadQueue() throws SQLException {
        Map<Integer, List<DomainToMigrate>> domainsBySource = new HashMap<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT EC_DOMAIN.ID, EC_DOMAIN.DOMAIN_NAME, EC_DOMAIN.NODE_AFFINITY
                    FROM DOMAIN_MIGRATION_QUEUE
                    JOIN EC_DOMAIN ON EC_DOMAIN.ID = DOMAIN_MIGRATION_QUEUE.DOMAIN_ID
                    WHERE DOMAIN_MIGRATION_QUEUE.DEST_NODE = ? AND DOMAIN_MIGRATION_QUEUE.STATE = 'NEW'
                    """))
        {
            stmt.setInt(1, nodeId);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                int sourceNode = rs.getInt("NODE_AFFINITY");
                domainsBySource.computeIfAbsent(sourceNode, k -> new ArrayList<>())
                        .add(new DomainToMigrate(rs.getInt("ID"), rs.getString("DOMAIN_NAME")));
            }
        }

        return domainsBySource;
    }

    /** Take ownership of a domain by pointing its node affinity here and marking the queue row
     * migrated.  Committed as a unit so a partial run leaves already migrated domains consistent.
     */
    private void takeOwnership(int domainId) throws SQLException {
        try (var conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (var affinityStmt = conn.prepareStatement("UPDATE EC_DOMAIN SET NODE_AFFINITY = ? WHERE ID = ?");
                 var queueStmt = conn.prepareStatement("UPDATE DOMAIN_MIGRATION_QUEUE SET STATE = 'MIGRATED' WHERE DOMAIN_ID = ?"))
            {
                affinityStmt.setInt(1, nodeId);
                affinityStmt.setInt(2, domainId);
                affinityStmt.executeUpdate();

                queueStmt.setInt(1, domainId);
                queueStmt.executeUpdate();

                conn.commit();
            }
            catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            finally {
                // HikariCP's auto-reset of this property is flimsy at best
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    /** Returns an empty map if the source has no crawler.log
     */
    private Map<String, WorkLogEntry> downloadCrawlerLog(HttpClient httpClient, FileStorage sourceStorage)
            throws IOException, InterruptedException
    {
        Path tempLog = Files.createTempFile("crawler", ".log");
        try {
            URL url = executorClient.remoteFileURL(sourceStorage, "crawler.log");
            if (url == null || !downloadToFile(httpClient, url, tempLog)) {
                return Map.of();
            }

            Map<String, WorkLogEntry> entriesByDomain = new HashMap<>();
            for (var entry : WorkLog.iterable(tempLog)) {
                entriesByDomain.put(entry.id(), entry);
            }
            return entriesByDomain;
        }
        finally {
            Files.deleteIfExists(tempLog);
        }
    }

    private Path downloadDomainStateDb(HttpClient httpClient, FileStorage sourceStorage)
            throws IOException, InterruptedException
    {
        Path tempDb = Files.createTempFile("domainstate", ".db");
        URL url = executorClient.remoteFileURL(sourceStorage, "domainstate.db");
        if (url == null || !downloadToFile(httpClient, url, tempDb)) {
            Files.deleteIfExists(tempDb);
            return null;
        }
        return tempDb;
    }

    private void carryOverDomainState(DomainStateDb sourceStateDb, DomainStateDb localStateDb, String domain) {
        sourceStateDb.getMeta(domain).ifPresent(localStateDb::save);
        sourceStateDb.getSummary(domain).ifPresent(localStateDb::save);
        sourceStateDb.getIcon(domain).ifPresent(icon -> localStateDb.saveIcon(domain, icon));
    }

    private boolean copyRemoteFile(HttpClient httpClient, FileStorage sourceStorage, String relPath, Path localPath)
            throws IOException, InterruptedException
    {
        URL url = executorClient.remoteFileURL(sourceStorage, relPath);
        if (url == null) {
            return false;
        }
        Files.createDirectories(localPath.getParent());
        return downloadToFile(httpClient, url, localPath);
    }

    /** Stream a remote file to disk. */
    private boolean downloadToFile(HttpClient httpClient, URL url, Path destination)
            throws IOException, InterruptedException
    {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(url.toURI()).GET().build();
        }
        catch (URISyntaxException ex) {
            throw new IOException("Malformed transfer URL " + url, ex);
        }

        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));

        return switch (response.statusCode()) {
            case 200 -> true;
            case 404 -> {
                Files.deleteIfExists(destination);
                yield false;
            }
            default -> {
                Files.deleteIfExists(destination);
                throw new IOException("Unexpected response " + response.statusCode() + " fetching " + url);
            }
        };
    }

    @Override
    public String describe() {
        return "Migrate crawl data for queued domains onto this node and take ownership of them.";
    }

    @Inject
    public MigrateDomainsActor(Gson gson,
                               FileStorageService storageService,
                               HikariDataSource dataSource,
                               ExecutorClient executorClient,
                               ServiceConfiguration configuration,
                               ServiceEventLog eventLog,
                               ServiceHeartbeat heartbeat)
    {
        super(gson);
        this.storageService = storageService;
        this.dataSource = dataSource;
        this.executorClient = executorClient;
        this.eventLog = eventLog;
        this.heartbeat = heartbeat;
        this.nodeId = configuration.node();
    }

    private record DomainToMigrate(int id, String name) {}
}
