package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.RedirectControl;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.node.model.*;
import nu.marginalia.control.sys.model.EventLogEntry;
import nu.marginalia.control.sys.svc.EventLogService;
import nu.marginalia.control.sys.svc.HeartbeatService;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.ServiceMonitors;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

public class ControlNodeService {
    private final FileStorageService fileStorageService;
    private final ControlRendererFactory rendererFactory;
    private final EventLogService eventLogService;
    private final HeartbeatService heartbeatService;
    private final ExecutorClient executorClient;
    private final HikariDataSource dataSource;
    private final ServiceMonitors monitors;
    private final RedirectControl redirectControl;
    private final NodeConfigurationService nodeConfigurationService;

    private final ControlCrawlDataService crawlDataService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ControlNodeService(
            FileStorageService fileStorageService,
            ControlRendererFactory rendererFactory,
            EventLogService eventLogService,
            HeartbeatService heartbeatService,
            ExecutorClient executorClient,
            HikariDataSource dataSource,
            ServiceMonitors monitors,
            RedirectControl redirectControl,
            NodeConfigurationService nodeConfigurationService,
            ControlCrawlDataService crawlDataService)
    {
        this.fileStorageService = fileStorageService;
        this.rendererFactory = rendererFactory;
        this.eventLogService = eventLogService;
        this.heartbeatService = heartbeatService;
        this.executorClient = executorClient;
        this.dataSource = dataSource;
        this.monitors = monitors;
        this.redirectControl = redirectControl;
        this.nodeConfigurationService = nodeConfigurationService;
        this.crawlDataService = crawlDataService;
    }

    public void register(Jooby jooby) throws IOException {
        var nodeListRenderer = rendererFactory.renderer("control/node/nodes-list");
        var overviewRenderer = rendererFactory.renderer("control/node/node-overview");
        var actionsRenderer = rendererFactory.renderer("control/node/node-actions");
        var actorsRenderer = rendererFactory.renderer("control/node/node-actors");
        var storageConfRenderer = rendererFactory.renderer("control/node/node-storage-conf");
        var storageListRenderer = rendererFactory.renderer("control/node/node-storage-list");
        var storageDetailsRenderer = rendererFactory.renderer("control/node/node-storage-details");
        var storageCrawlParquetDetails = rendererFactory.renderer("control/node/node-storage-crawl-parquet-details");
        var configRenderer = rendererFactory.renderer("control/node/node-config");


        jooby.get("/nodes", ctx -> nodeListRenderer.render(nodeListModel(ctx)));
        jooby.get("/nodes/{id}", ctx -> overviewRenderer.render(nodeOverviewModel(ctx)));
        jooby.get("/nodes/{id}/", ctx -> overviewRenderer.render(nodeOverviewModel(ctx)));
        jooby.get("/nodes/{id}/actors", ctx -> actorsRenderer.render(nodeActorsModel(ctx)));
        jooby.get("/nodes/{id}/actions", ctx -> actionsRenderer.render(nodeActionsModel(ctx)));
        jooby.get("/nodes/{id}/storage/", ctx -> storageConfRenderer.render(nodeStorageConfModel(ctx)));
        jooby.get("/nodes/{id}/storage/conf", ctx -> storageConfRenderer.render(nodeStorageConfModel(ctx)));
        jooby.get("/nodes/{id}/storage/details", ctx -> storageDetailsRenderer.render(nodeStorageDetailsModel(ctx)));

        jooby.get("/nodes/{id}/storage/crawl-parquet-info", ctx -> storageCrawlParquetDetails.render(crawlDataService.crawlParquetInfo(ctx)));

        jooby.post("/nodes/{id}/process/{processBase}/stop", ctx -> redirectControl.renderRedirectAcknowledgement("Stopping", "../..").render(stopProcess(ctx)));

        jooby.get("/nodes/{id}/storage/{view}", ctx -> storageListRenderer.render(nodeStorageListModel(ctx)));

        jooby.get("/nodes/{id}/configuration", ctx -> configRenderer.render(nodeConfigModel(ctx)));
        jooby.post("/nodes/{id}/configuration", ctx -> configRenderer.render(updateConfigModel(ctx)));

        jooby.post("/nodes/{id}/storage/reset-state/{fid}", ctx -> redirectControl.renderRedirectAcknowledgement("Restoring", "..").render(resetState(ctx)));
        jooby.post("/nodes/{id}/fsms/{fsm}/start", this::startFsm);
        jooby.post("/nodes/{id}/fsms/{fsm}/stop", this::stopFsm);
    }

    private Object resetState(Context ctx) throws SQLException {
        fileStorageService.setFileStorageState(FileStorageId.parse(ctx.path("fid").value()), FileStorageState.UNSET);
        return "";
    }

    public Object startFsm(Context ctx) throws Exception {
        executorClient.startFsm(Integer.parseInt(ctx.path("id").value()), ctx.path("fsm").value().toUpperCase());

        return redirectToOverview(ctx);
    }

    public Object stopFsm(Context ctx) throws Exception {
        executorClient.stopFsm(Integer.parseInt(ctx.path("id").value()), ctx.path("fsm").value().toUpperCase());

        return redirectToOverview(ctx);
    }

    private Object nodeListModel(Context ctx) throws SQLException {
        var configs = nodeConfigurationService.getAll();

        int nextId = configs.stream().mapToInt(NodeConfiguration::node).map(i -> i+1).max().orElse(1);

        return Map.of(
                "nodes", nodeConfigurationService.getAll(),
                "nextNodeId", nextId);
    }

    private Object stopProcess(Context ctx) {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        String processBase = ctx.path("processBase").value();

        executorClient.stopProcess(nodeId, processBase);

        return "";
    }

    public String redirectToOverview(int nodeId) {
        try {
            return new Redirects.HtmlRedirect("/nodes/"+nodeId).render(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String redirectToOverview(Context ctx) {
        return redirectToOverview(Integer.parseInt(ctx.path("id").value()));
    }

    private Object nodeActorsModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        return Map.of(
                "tab", Map.of("actors", true),
                "node", nodeConfigurationService.get(nodeId),
                "actors", executorClient.getActorStates(nodeId).states()
        );
    }

    private Object nodeActionsModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        return Map.of(
                "tab", Map.of("actions", true),
                "node", nodeConfigurationService.get(nodeId),
                "view", Map.of(ctx.lookup("view", QUERY, FORM).valueOrNull(), true),
                "uploadDirContents", executorClient.listSideloadDir(nodeId),
                "allBackups",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.BACKUP),
                "allCrawlData",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.CRAWL_DATA),
                "allProcessedData",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.PROCESSED_DATA)
        );
    }

    private Object nodeStorageConfModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        return Map.of(
                "tab", Map.of("storage", true),
                "view", Map.of("conf", true),
                "node", nodeConfigurationService.get(nodeId),
                "storagebase", getStorageBaseList(nodeId)
        );
    }


    private Object nodeStorageListModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        String view = ctx.path("view").value();

        FileStorageType type = switch(view) {
            case "backup" -> FileStorageType.BACKUP;
            case "crawl" -> FileStorageType.CRAWL_DATA;
            case "processed" -> FileStorageType.PROCESSED_DATA;
            case "exports" -> FileStorageType.EXPORT;
            default -> throw new IllegalArgumentException(view);
        };

        return Map.of(
                "tab", Map.of("storage", true),
                "view", Map.of(view, true),
                "node", nodeConfigurationService.get(nodeId),
                "storage", makeFileStorageBaseWithStorage(getFileStorageIds(type, nodeId))
        );
    }

    private Object nodeStorageDetailsModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        var fsid = FileStorageId.parse(ctx.lookup("fid", QUERY, FORM).valueOrNull());
        var storage = getFileStorageWithRelatedEntries(nodeId, fsid);

        String view = switch(storage.type()) {
            case BACKUP -> "backup";
            case CRAWL_DATA -> "crawl";
            case CRAWL_SPEC -> "specs";
            case PROCESSED_DATA -> "processed";
            case EXPORT -> "exports";
            default -> throw new IllegalStateException(storage.type().toString());
        };

        var ret = new HashMap<>();

        ret.put("tab", Map.of("storage", true));
        ret.put("view", Map.of(view, true));
        ret.put("node", nodeConfigurationService.get(nodeId));
        ret.put("storage", storage);

        if (storage.type() == FileStorageType.CRAWL_DATA) {
            var cdFiles = crawlDataService.getCrawlDataFiles(fsid,
                    ctx.lookup("filterDomain", QUERY, FORM).valueOrNull(),
                    ctx.lookup("afterDomain", QUERY, FORM).valueOrNull()
            );
            ret.put("crawlDataFiles", cdFiles);
        }

        return ret;

    }


    private Object nodeConfigModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        Map<String, Path> storage = new HashMap<>();

        for (var baseType : List.of(FileStorageBaseType.CURRENT, FileStorageBaseType.WORK, FileStorageBaseType.BACKUP, FileStorageBaseType.STORAGE)) {
            Optional.ofNullable(fileStorageService.getStorageBase(baseType, nodeId))
                    .map(FileStorageBase::asPath)
                    .ifPresent(path -> storage.put(baseType.toString().toLowerCase(), path));
        }

        return Map.of(
                "tab", Map.of("config", true),
                "node", nodeConfigurationService.get(nodeId),
                "config", Objects.requireNonNull(nodeConfigurationService.get(nodeId), "Failed to fetch configuration"),
                "storage", storage);
    }

    private Object updateConfigModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        String act = ctx.lookup("act", QUERY, FORM).valueOrNull();

        if ("config".equals(act)) {
            var oldConfig = nodeConfigurationService.get(nodeId);

            var newConfig = new NodeConfiguration(
                    nodeId,
                    ctx.lookup("description", QUERY, FORM).valueOrNull(),
                    "on".equalsIgnoreCase(ctx.lookup("acceptQueries", QUERY, FORM).valueOrNull()),
                    "on".equalsIgnoreCase(ctx.lookup("autoClean", QUERY, FORM).valueOrNull()),
                    "on".equalsIgnoreCase(ctx.lookup("includeInPrecession", QUERY, FORM).valueOrNull()),
                    "on".equalsIgnoreCase(ctx.lookup("keepWarcs", QUERY, FORM).valueOrNull()),
                    "on".equalsIgnoreCase(ctx.lookup("autoAssignDomains", QUERY, FORM).valueOrNull()),
                    NodeProfile.valueOf(ctx.lookup("profile", QUERY, FORM).valueOrNull()),
                    "on".equalsIgnoreCase(ctx.lookup("disabled", QUERY, FORM).valueOrNull())
            );

            nodeConfigurationService.save(newConfig);

            if (!(Objects.equals(oldConfig.profile(), newConfig.profile()))) {
                // Restart the executor service if the profile has changed
                executorClient.restartExecutorService(nodeId);
            }
            else if (newConfig.disabled()) {
                executorClient.restartExecutorService(nodeId);
            }
        }
        else if ("storage".equals(act)) {
            throw new UnsupportedOperationException();
        }
        else {
            throw new io.jooby.exception.BadRequestException("Invalid action");
        }

        return nodeConfigModel(ctx);
    }

    private Object nodeOverviewModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        var config = nodeConfigurationService.get(nodeId);

        var actors = executorClient.getActorStates(nodeId).states()
                .stream().filter(actor -> !actor.state().equals("MONITOR"))
                .toList();

        return Map.of(
                "node", nodeConfigurationService.get(nodeId),
                "status", getStatus(config),
                "events", getEvents(nodeId),
                "processes", heartbeatService.getProcessHeartbeatsForNode(nodeId),
                "jobs", heartbeatService.getTaskHeartbeatsForNode(nodeId),
                "actors", actors,
                "tab", Map.of("overview", true)
                );
    }

    private Object getStorageBaseList(int nodeId) throws SQLException {
        List<FileStorageBase> bases = new ArrayList<>();

        for (var type : FileStorageBaseType.values()) {
            var base = fileStorageService.getStorageBase(type, nodeId);
            bases.add(Objects.requireNonNullElseGet(base,
                    () -> new FileStorageBase(new FileStorageBaseId(-1), type, -1, "MISSING", "MISSING"))
            );
        }

        return bases;
    }

    private List<EventLogEntry> getEvents(int nodeId) {
        List<String> services = List.of(ServiceId.Index.serviceName +":"+nodeId);
        List<EventLogEntry> events = new ArrayList<>(20);
        for (var service :services) {
            events.addAll(eventLogService.getLastEntriesForService(service, Long.MAX_VALUE, 10));
        }
        events.sort(Comparator.comparing(EventLogEntry::id).reversed());
        return events;
    }

    public List<IndexNodeStatus> getNodeStatusList() {
        return nodeConfigurationService
                .getAll()
                .stream()
                .sorted(Comparator.comparing(NodeConfiguration::node))
                .map(this::getStatus)
                .toList();
    }

    public IndexNodeStatus getStatus(NodeConfiguration config) {
        return new IndexNodeStatus(config,
                monitors.isServiceUp(ServiceId.Index, config.node())
        );
    }

    private List<FileStorageId> getFileStorageIds(FileStorageType type, int node) throws SQLException {
        List<FileStorageId> storageIds = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var storageByIdStmt = conn.prepareStatement("""
                SELECT FILE_STORAGE.ID
                FROM FILE_STORAGE
                INNER JOIN FILE_STORAGE_BASE
                    ON BASE_ID=FILE_STORAGE_BASE.ID
                WHERE FILE_STORAGE.TYPE = ?
                AND NODE = ?
                """))
        {
            storageByIdStmt.setString(1, type.name());
            storageByIdStmt.setInt(2, node);
            var rs = storageByIdStmt.executeQuery();
            while (rs.next()) {
                storageIds.add(new FileStorageId(rs.getLong("ID")));
            }
        }

        return storageIds;
    }

    private List<FileStorageBaseWithStorage> makeFileStorageBaseWithStorage(List<FileStorageId> storageIds) throws SQLException {
        Map<FileStorageBaseId, FileStorageBase> fileStorageBaseByBaseId = new HashMap<>();
        Map<FileStorageBaseId, List<FileStorageWithActions>> fileStorageByBaseId = new HashMap<>();

        for (var id : storageIds) {
            var storage = fileStorageService.getStorage(id);
            fileStorageBaseByBaseId.computeIfAbsent(storage.base().id(), k -> storage.base());
            fileStorageByBaseId.computeIfAbsent(storage.base().id(), k -> new ArrayList<>()).add(new FileStorageWithActions(storage));
        }

        List<FileStorageBaseWithStorage> result = new ArrayList<>();

        for (var baseId : fileStorageBaseByBaseId.keySet()) {
            var base = fileStorageBaseByBaseId.get(baseId);
            var items = fileStorageByBaseId.get(baseId);

            // Sort by timestamp, then by relPath
            // this ensures that the newest file is listed last
            items.sort(Comparator
                    .comparing(FileStorageWithActions::getTimestampFull)
                    .thenComparing(FileStorageWithActions::getRelPath)
            );

            result.add(new FileStorageBaseWithStorage(base, items));
        }

        return result;
    }


    public FileStorageWithRelatedEntries getFileStorageWithRelatedEntries(
            int node,
            FileStorageId fileId
    ) throws SQLException {
        var storage = fileStorageService.getStorage(fileId);
        var related = getRelatedEntries(fileId);

        List<FileStorageFileModel> files = new ArrayList<>();

        for (var execFile : executorClient.listFileStorage(node, fileId).files()) {
            files.add(new FileStorageFileModel(
                    execFile.name(),
                    execFile.modTime(),
                    sizeString(execFile.size())
            ));
        }

        return new FileStorageWithRelatedEntries(new FileStorageWithActions(storage), related, files);
    }

    private String sizeString(long sizeBytes) {
        String size;

        if (sizeBytes < 1024) size = sizeBytes + " B";
        else if (sizeBytes < 1024 * 1024) size = sizeBytes / 1024 + " KB";
        else if (sizeBytes < 1024 * 1024 * 1024) size = sizeBytes / (1024 * 1024) + " MB";
        else size = sizeBytes / (1024 * 1024 * 1024) + " GB";
        return size;
    }

    private List<FileStorage> getRelatedEntries(FileStorageId id) {
        List<FileStorage> ret = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var relatedIds = conn.prepareStatement("""
                     (SELECT SOURCE_ID AS ID FROM FILE_STORAGE_RELATION WHERE TARGET_ID = ?)
                     UNION
                     (SELECT TARGET_ID AS ID FROM FILE_STORAGE_RELATION WHERE SOURCE_ID = ?)
                     """))
        {

            relatedIds.setLong(1, id.id());
            relatedIds.setLong(2, id.id());
            var rs = relatedIds.executeQuery();
            while (rs.next()) {
                ret.add(fileStorageService.getStorage(new FileStorageId(rs.getLong("ID"))));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return ret;
    }

}
