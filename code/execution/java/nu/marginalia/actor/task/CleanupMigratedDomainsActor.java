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
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.process.log.WorkLogEntry;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Singleton
public class CleanupMigratedDomainsActor extends RecordActorPrototype {

    private static final String SLOP_SUFFIX = ".slop.zip";

    private final FileStorageService storageService;
    private final HikariDataSource dataSource;
    private final ServiceEventLog eventLog;
    private final ServiceHeartbeat heartbeat;
    private final int nodeId;

    @Resume(behavior = ActorResumeBehavior.ERROR)
    public record Initial() implements ActorStep {}

    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Cleanup(FileStorageId storageId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> {
                var storageId = storageService.getOnlyActiveFileStorage(FileStorageType.CRAWL_DATA);
                if (storageId.isEmpty()) {
                    yield new End();
                }
                yield new Cleanup(storageId.get());
            }
            case Cleanup(FileStorageId storageId) -> {
                Path base = storageService.getStorage(storageId).asPath();
                Path logPath = base.resolve("crawler.log");

                if (!Files.exists(logPath)) {
                    eventLog.logEvent(getClass().getSimpleName(), "No crawler.log in storage, nothing to clean");
                    yield new End();
                }

                // The crawler.log is the authoritative mapping from a slop.zip file to its domain,
                // the safe filenames on disk cannot be reversed back to a domain reliably.
                Map<String, WorkLogEntry> entryByDomain = new LinkedHashMap<>();
                Map<String, String> domainByFilename = new HashMap<>();
                for (var entry : WorkLog.iterable(logPath)) {
                    entryByDomain.put(entry.id(), entry);
                    domainByFilename.put(entry.fileName(), entry.id());
                }

                Set<String> domainsAssignedHere = loadDomainsAssignedHere(entryByDomain.keySet());

                int deleted = deleteForeignFiles(base, domainByFilename, domainsAssignedHere);
                pruneDomainState(base, entryByDomain.keySet(), domainsAssignedHere);
                rewriteCrawlerLog(base, logPath, entryByDomain, domainsAssignedHere);

                eventLog.logEvent(getClass().getSimpleName(),
                        "Cleanup complete, deleted " + deleted + " foreign crawl data files");
                yield new End();
            }
            default -> new Error();
        };
    }

    /** Delete every *.slop.zip whose domain is not assigned to this node, including orphan files
     * not referenced by the crawler.log.  Returns the number of files deleted.
     */
    private int deleteForeignFiles(Path base,
                                   Map<String, String> domainByFilename,
                                   Set<String> domainsAssignedHere) throws IOException
    {
        List<Path> slopFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(base)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(SLOP_SUFFIX))
                    .forEach(slopFiles::add);
        }

        int deleted = 0;
        try (var hb = heartbeat.createServiceAdHocTaskHeartbeat("Cleaning crawl data")) {
            for (var file : hb.wrap("cleanup", slopFiles)) {
                String domain = domainByFilename.get(file.getFileName().toString());
                boolean assignedHere = domain != null && domainsAssignedHere.contains(domain.toLowerCase());
                if (!assignedHere) {
                    Files.delete(file);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    /** Rewrite crawler.log so it references only the files that were retained, replacing the old
     * log atomically.
     */
    private void rewriteCrawlerLog(Path base,
                                   Path logPath,
                                   Map<String, WorkLogEntry> entryByDomain,
                                   Set<String> domainsAssignedHere) throws IOException
    {
        Path newLogWorkPath = Files.createTempFile(base, "crawler", ".log");

        try (WorkLog cleanLog = new WorkLog(newLogWorkPath)) {
            for (var entry : entryByDomain.entrySet()) {
                if (domainsAssignedHere.contains(entry.getKey().toLowerCase())) {
                    WorkLogEntry logEntry = entry.getValue();
                    cleanLog.setJobToFinished(logEntry.id(), logEntry.path(), logEntry.cnt());
                }
            }
        }

        Path backupPath = logPath.resolveSibling(logPath.getFileName() + ".bak");
        Files.move(logPath, backupPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        Files.move(newLogWorkPath, logPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void pruneDomainState(Path base, Iterable<String> domains, Set<String> domainsAssignedHere) throws SQLException {
        Path stateDbPath = base.resolve("domainstate.db");
        if (!Files.exists(stateDbPath)) {
            return;
        }

        try (DomainStateDb stateDb = new DomainStateDb(stateDbPath)) {
            for (String domain : domains) {
                if (!domainsAssignedHere.contains(domain.toLowerCase())) {
                    stateDb.deleteDomain(domain);
                }
            }
        }
    }

    private Set<String> loadDomainsAssignedHere(Set<String> candidates) throws SQLException {
        Set<String> domainsAssignedHere = new HashSet<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE NODE_AFFINITY = ?"))
        {
            stmt.setFetchSize(10_000);
            stmt.setInt(1, nodeId);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                String domain = rs.getString("DOMAIN_NAME");
                if (candidates.contains(domain)) {
                    domainsAssignedHere.add(domain.toLowerCase());
                }
            }
        }

        return domainsAssignedHere;
    }

    @Override
    public String describe() {
        return "Delete crawl data for domains no longer assigned to this node and rewrite a clean crawler.log.";
    }

    @Inject
    public CleanupMigratedDomainsActor(Gson gson,
                                       FileStorageService storageService,
                                       HikariDataSource dataSource,
                                       ServiceConfiguration configuration,
                                       ServiceEventLog eventLog,
                                       ServiceHeartbeat heartbeat)
    {
        super(gson);
        this.storageService = storageService;
        this.dataSource = dataSource;
        this.eventLog = eventLog;
        this.heartbeat = heartbeat;
        this.nodeId = configuration.node();
    }
}
