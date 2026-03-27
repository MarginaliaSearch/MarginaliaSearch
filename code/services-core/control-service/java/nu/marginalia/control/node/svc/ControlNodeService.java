package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import io.jooby.StatusCode;
import io.jooby.exception.StatusCodeException;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.RedirectControl;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.node.model.*;
import nu.marginalia.control.sys.model.EventLogEntry;
import nu.marginalia.control.sys.svc.EventLogService;
import nu.marginalia.control.sys.svc.HeartbeatService;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.executor.storage.FileStorageFile;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.executor.model.ActorRunState;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.ServiceMonitors;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    private ControlRendererFactory.Renderer nodeListRenderer;
    private ControlRendererFactory.Renderer overviewRenderer;
    private ControlRendererFactory.Renderer actionsRenderer;
    private ControlRendererFactory.Renderer actorsRenderer;
    private ControlRendererFactory.Renderer storageConfRenderer;
    private ControlRendererFactory.Renderer storageListRenderer;
    private ControlRendererFactory.Renderer storageDetailsRenderer;
    private ControlRendererFactory.Renderer storageCrawlParquetDetailsRenderer;
    private ControlRendererFactory.Renderer configRenderer;

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
        this.nodeListRenderer = rendererFactory.renderer("control/node/nodes-list");
        this.overviewRenderer = rendererFactory.renderer("control/node/node-overview");
        this.actionsRenderer = rendererFactory.renderer("control/node/node-actions");
        this.actorsRenderer = rendererFactory.renderer("control/node/node-actors");
        this.storageConfRenderer = rendererFactory.renderer("control/node/node-storage-conf");
        this.storageListRenderer = rendererFactory.renderer("control/node/node-storage-list");
        this.storageDetailsRenderer = rendererFactory.renderer("control/node/node-storage-details");
        this.storageCrawlParquetDetailsRenderer = rendererFactory.renderer("control/node/node-storage-crawl-parquet-details");
        this.configRenderer = rendererFactory.renderer("control/node/node-config");

        jooby.get("/nodes", this::nodeListModel);
        jooby.get("/nodes/{id}", this::nodeOverviewModel);
        jooby.get("/nodes/{id}/", this::nodeOverviewModel);
        jooby.get("/nodes/{id}/actors", this::nodeActorsModel);
        jooby.get("/nodes/{id}/actions", this::nodeActionsModel);
        jooby.get("/nodes/{id}/storage/", this::nodeStorageConfModel);
        jooby.get("/nodes/{id}/storage/conf", this::nodeStorageConfModel);
        jooby.get("/nodes/{id}/storage/details", this::nodeStorageDetailsModel);
        jooby.get("/nodes/{id}/storage/crawl-parquet-info", this::handleCrawlParquetInfo);
        jooby.post("/nodes/{id}/process/{processBase}/stop", this::stopProcess);
        jooby.get("/nodes/{id}/storage/{view}", this::nodeStorageListModel);
        jooby.get("/nodes/{id}/configuration", this::handleGetConfiguration);
        jooby.post("/nodes/{id}/configuration", this::updateConfigModel);
        jooby.post("/nodes/{id}/storage/reset-state/{fid}", this::resetState);
        jooby.post("/nodes/{id}/fsms/{fsm}/start", this::startFsm);
        jooby.post("/nodes/{id}/fsms/{fsm}/stop", this::stopFsm);
    }

    private String nodeListModel(Context ctx) throws Exception {
        List<NodeConfiguration> configs = nodeConfigurationService.getAll();
        int nextId = configs.stream().mapToInt(NodeConfiguration::node).map(i -> i+1).max().orElse(1);

        ctx.setResponseType(MediaType.html);
        return nodeListRenderer.render(Map.of(
                "nodes", nodeConfigurationService.getAll(),
                "nextNodeId", nextId));
    }

    private String nodeOverviewModel(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        NodeConfiguration config = nodeConfigurationService.get(nodeId);

        List<ActorRunState> actors = executorClient.getActorStates(nodeId).states()
                .stream().filter(actor -> !actor.state().equals("MONITOR"))
                .toList();

        ctx.setResponseType(MediaType.html);
        return overviewRenderer.render(Map.of(
                "node", nodeConfigurationService.get(nodeId),
                "status", getStatus(config),
                "events", getEvents(nodeId),
                "processes", heartbeatService.getProcessHeartbeatsForNode(nodeId),
                "jobs", heartbeatService.getTaskHeartbeatsForNode(nodeId),
                "actors", actors,
                "tab", Map.of("overview", true)
        ));
    }

    private String nodeActorsModel(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        ctx.setResponseType(MediaType.html);
        return actorsRenderer.render(Map.of(
                "tab", Map.of("actors", true),
                "node", nodeConfigurationService.get(nodeId),
                "actors", executorClient.getActorStates(nodeId).states()
        ));
    }

    private String nodeActionsModel(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        ctx.setResponseType(MediaType.html);
        return actionsRenderer.render(Map.of(
                "tab", Map.of("actions", true),
                "node", nodeConfigurationService.get(nodeId),
                "view", Map.of(ctx.query("view").value(""), true),
                "uploadDirContents", executorClient.listSideloadDir(nodeId),
                "allBackups",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.BACKUP),
                "allCrawlData",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.CRAWL_DATA),
                "allProcessedData",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.PROCESSED_DATA)
        ));
    }

    private String nodeStorageConfModel(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        ctx.setResponseType(MediaType.html);
        return storageConfRenderer.render(Map.of(
                "tab", Map.of("storage", true),
                "view", Map.of("conf", true),
                "node", nodeConfigurationService.get(nodeId),
                "storagebase", getStorageBaseList(nodeId)
        ));
    }

    private String nodeStorageDetailsModel(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        FileStorageId fsid = FileStorageId.parse(ctx.query("fid").valueOrNull());
        FileStorageWithRelatedEntries storage = getFileStorageWithRelatedEntries(nodeId, fsid);

        String view = switch(storage.type()) {
            case BACKUP -> "backup";
            case CRAWL_DATA -> "crawl";
            case CRAWL_SPEC -> "specs";
            case PROCESSED_DATA -> "processed";
            case EXPORT -> "exports";
            default -> throw new IllegalStateException(storage.type().toString());
        };

        HashMap<String, Object> ret = new HashMap<>();

        ret.put("tab", Map.of("storage", true));
        ret.put("view", Map.of(view, true));
        ret.put("node", nodeConfigurationService.get(nodeId));
        ret.put("storage", storage);

        if (storage.type() == FileStorageType.CRAWL_DATA) {
            ControlCrawlDataService.CrawlDataFileList cdFiles = crawlDataService.getCrawlDataFiles(fsid,
                    ctx.query("filterDomain").valueOrNull(),
                    ctx.query("afterDomain").valueOrNull()
            );
            ret.put("crawlDataFiles", cdFiles);
        }

        ctx.setResponseType(MediaType.html);
        return storageDetailsRenderer.render(ret);
    }

    private String handleCrawlParquetInfo(Context ctx) throws Exception {
        ctx.setResponseType(MediaType.html);
        return storageCrawlParquetDetailsRenderer.render(crawlDataService.crawlParquetInfo(ctx));
    }

    private String stopProcess(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        String processBase = ctx.path("processBase").value();

        executorClient.stopProcess(nodeId, processBase);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Stopping", "../..");
    }

    private String nodeStorageListModel(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        String view = ctx.path("view").value();

        FileStorageType type = switch(view) {
            case "backup" -> FileStorageType.BACKUP;
            case "crawl" -> FileStorageType.CRAWL_DATA;
            case "processed" -> FileStorageType.PROCESSED_DATA;
            case "exports" -> FileStorageType.EXPORT;
            default -> throw new IllegalArgumentException(view);
        };

        ctx.setResponseType(MediaType.html);
        return storageListRenderer.render(Map.of(
                "tab", Map.of("storage", true),
                "view", Map.of(view, true),
                "node", nodeConfigurationService.get(nodeId),
                "storage", makeFileStorageBaseWithStorage(getFileStorageIds(type, nodeId))
        ));
    }

    private String handleGetConfiguration(Context ctx) throws Exception {
        ctx.setResponseType(MediaType.html);
        return configRenderer.render(nodeConfigModel(ctx));
    }

    private String updateConfigModel(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        String act = ctx.query("act").valueOrNull();

        if ("config".equals(act)) {
            NodeConfiguration oldConfig = nodeConfigurationService.get(nodeId);

            NodeConfiguration newConfig = new NodeConfiguration(
                    nodeId,
                    ctx.form("description").valueOrNull(),
                    "on".equalsIgnoreCase(ctx.form("acceptQueries").valueOrNull()),
                    "on".equalsIgnoreCase(ctx.form("autoClean").valueOrNull()),
                    "on".equalsIgnoreCase(ctx.form("includeInPrecession").valueOrNull()),
                    "on".equalsIgnoreCase(ctx.form("keepWarcs").valueOrNull()),
                    "on".equalsIgnoreCase(ctx.form("autoAssignDomains").valueOrNull()),
                    NodeProfile.valueOf(ctx.form("profile").valueOrNull()),
                    "on".equalsIgnoreCase(ctx.form("disabled").valueOrNull())
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
            throw new StatusCodeException(StatusCode.BAD_REQUEST);
        }

        ctx.setResponseType(MediaType.html);
        return configRenderer.render(nodeConfigModel(ctx));
    }

    private String resetState(Context ctx) throws Exception {
        fileStorageService.setFileStorageState(FileStorageId.parse(ctx.path("fid").value()), FileStorageState.UNSET);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Restoring", "..");
    }

    private Object startFsm(Context ctx) throws Exception {
        executorClient.startFsm(Integer.parseInt(ctx.path("id").value()), ctx.path("fsm").value().toUpperCase());

        ctx.setResponseType(MediaType.html);
        return new Redirects.HtmlRedirect("/nodes/" + Integer.parseInt(ctx.path("id").value())).render(null);
    }

    private Object stopFsm(Context ctx) throws Exception {
        executorClient.stopFsm(Integer.parseInt(ctx.path("id").value()), ctx.path("fsm").value().toUpperCase());

        ctx.setResponseType(MediaType.html);
        return new Redirects.HtmlRedirect("/nodes/" + Integer.parseInt(ctx.path("id").value())).render(null);
    }

    private Object nodeConfigModel(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        Map<String, Path> storage = new HashMap<>();

        for (FileStorageBaseType baseType : List.of(FileStorageBaseType.CURRENT, FileStorageBaseType.WORK, FileStorageBaseType.BACKUP, FileStorageBaseType.STORAGE)) {
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

    private Object getStorageBaseList(int nodeId) throws SQLException {
        List<FileStorageBase> bases = new ArrayList<>();

        for (FileStorageBaseType type : FileStorageBaseType.values()) {
            FileStorageBase base = fileStorageService.getStorageBase(type, nodeId);
            bases.add(Objects.requireNonNullElseGet(base,
                    () -> new FileStorageBase(new FileStorageBaseId(-1), type, -1, "MISSING", "MISSING"))
            );
        }

        return bases;
    }

    private List<EventLogEntry> getEvents(int nodeId) {
        List<String> services = List.of(ServiceId.Index.serviceName +":"+nodeId);
        List<EventLogEntry> events = new ArrayList<>(20);
        for (String service : services) {
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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement storageByIdStmt = conn.prepareStatement("""
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
            ResultSet rs = storageByIdStmt.executeQuery();
            while (rs.next()) {
                storageIds.add(new FileStorageId(rs.getLong("ID")));
            }
        }

        return storageIds;
    }

    private List<FileStorageBaseWithStorage> makeFileStorageBaseWithStorage(List<FileStorageId> storageIds) throws SQLException {
        Map<FileStorageBaseId, FileStorageBase> fileStorageBaseByBaseId = new HashMap<>();
        Map<FileStorageBaseId, List<FileStorageWithActions>> fileStorageByBaseId = new HashMap<>();

        for (FileStorageId id : storageIds) {
            FileStorage storage = fileStorageService.getStorage(id);
            fileStorageBaseByBaseId.computeIfAbsent(storage.base().id(), k -> storage.base());
            fileStorageByBaseId.computeIfAbsent(storage.base().id(), k -> new ArrayList<>()).add(new FileStorageWithActions(storage));
        }

        List<FileStorageBaseWithStorage> result = new ArrayList<>();

        for (FileStorageBaseId baseId : fileStorageBaseByBaseId.keySet()) {
            FileStorageBase base = fileStorageBaseByBaseId.get(baseId);
            List<FileStorageWithActions> items = fileStorageByBaseId.get(baseId);

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
        FileStorage storage = fileStorageService.getStorage(fileId);
        List<FileStorage> related = getRelatedEntries(fileId);

        List<FileStorageFileModel> files = new ArrayList<>();

        for (FileStorageFile execFile : executorClient.listFileStorage(node, fileId).files()) {
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement relatedIds = conn.prepareStatement("""
                     (SELECT SOURCE_ID AS ID FROM FILE_STORAGE_RELATION WHERE TARGET_ID = ?)
                     UNION
                     (SELECT TARGET_ID AS ID FROM FILE_STORAGE_RELATION WHERE SOURCE_ID = ?)
                     """))
        {

            relatedIds.setLong(1, id.id());
            relatedIds.setLong(2, id.id());
            ResultSet rs = relatedIds.executeQuery();
            while (rs.next()) {
                ret.add(fileStorageService.getStorage(new FileStorageId(rs.getLong("ID"))));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return ret;
    }

}
