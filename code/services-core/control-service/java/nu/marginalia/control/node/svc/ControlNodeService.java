package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
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
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.ServiceMonitors;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

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
            NodeConfigurationService nodeConfigurationService, ControlCrawlDataService crawlDataService)
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

    public void register() throws IOException {
        var nodeListRenderer = rendererFactory.renderer("control/node/nodes-list");
        var overviewRenderer = rendererFactory.renderer("control/node/node-overview");
        var actionsRenderer = rendererFactory.renderer("control/node/node-actions");
        var actorsRenderer = rendererFactory.renderer("control/node/node-actors");
        var storageConfRenderer = rendererFactory.renderer("control/node/node-storage-conf");
        var storageListRenderer = rendererFactory.renderer("control/node/node-storage-list");
        var storageDetailsRenderer = rendererFactory.renderer("control/node/node-storage-details");
        var storageCrawlParquetDetails = rendererFactory.renderer("control/node/node-storage-crawl-parquet-details");
        var configRenderer = rendererFactory.renderer("control/node/node-config");


        Spark.get("/nodes", this::nodeListModel, nodeListRenderer::render);
        Spark.get("/nodes/:id", this::nodeOverviewModel, overviewRenderer::render);
        Spark.get("/nodes/:id/", this::nodeOverviewModel, overviewRenderer::render);
        Spark.get("/nodes/:id/actors", this::nodeActorsModel, actorsRenderer::render);
        Spark.get("/nodes/:id/actions", this::nodeActionsModel, actionsRenderer::render);
        Spark.get("/nodes/:id/storage/", this::nodeStorageConfModel, storageConfRenderer::render);
        Spark.get("/nodes/:id/storage/conf", this::nodeStorageConfModel, storageConfRenderer::render);
        Spark.get("/nodes/:id/storage/details", this::nodeStorageDetailsModel, storageDetailsRenderer::render);

        Spark.get("/nodes/:id/storage/crawl-parquet-info", crawlDataService::crawlParquetInfo, storageCrawlParquetDetails::render);

        Spark.post("/nodes/:id/process/:processBase/stop", this::stopProcess,
                redirectControl.renderRedirectAcknowledgement("Stopping", "../..")
        );

        Spark.get("/nodes/:id/storage/:view", this::nodeStorageListModel, storageListRenderer::render);

        Spark.get("/nodes/:id/configuration", this::nodeConfigModel, configRenderer::render);
        Spark.post("/nodes/:id/configuration", this::updateConfigModel, configRenderer::render);

        Spark.post("/nodes/:id/storage/reset-state/:fid", this::resetState,
                redirectControl.renderRedirectAcknowledgement("Restoring", "..")
        );
        Spark.post("/nodes/:id/fsms/:fsm/start", this::startFsm);
        Spark.post("/nodes/:id/fsms/:fsm/stop", this::stopFsm);
    }

    private Object resetState(Request request, Response response) throws SQLException {
        fileStorageService.setFileStorageState(FileStorageId.parse(request.params("fid")), FileStorageState.UNSET);
        return "";
    }

    public Object startFsm(Request req, Response rsp) throws Exception {
        executorClient.startFsm(Integer.parseInt(req.params("id")), req.params("fsm").toUpperCase());

        return redirectToOverview(req);
    }

    public Object stopFsm(Request req, Response rsp) throws Exception {
        executorClient.stopFsm(Integer.parseInt(req.params("id")), req.params("fsm").toUpperCase());

        return redirectToOverview(req);
    }

    private Object nodeListModel(Request request, Response response) throws SQLException {
        var configs = nodeConfigurationService.getAll();

        int nextId = configs.stream().mapToInt(NodeConfiguration::node).map(i -> i+1).max().orElse(1);

        return Map.of(
                "nodes", nodeConfigurationService.getAll(),
                "nextNodeId", nextId);
    }

    private Object stopProcess(Request request, Response response) {
        int nodeId = Integer.parseInt(request.params("id"));
        String processBase = request.params("processBase");

        executorClient.stopProcess(nodeId, processBase);

        return "";
    }

    @SneakyThrows
    public String redirectToOverview(int nodeId) {
        return new Redirects.HtmlRedirect("/nodes/"+nodeId).render(null);
    }

    @SneakyThrows
    public String redirectToOverview(Request request) {
        return redirectToOverview(Integer.parseInt(request.params("id")));
    }

    private Object nodeActorsModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        return Map.of(
                "tab", Map.of("actors", true),
                "node", nodeConfigurationService.get(nodeId),
                "actors", executorClient.getActorStates(nodeId).states()
        );
    }

    private Object nodeActionsModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        return Map.of(
                "tab", Map.of("actions", true),
                "node", nodeConfigurationService.get(nodeId),
                "view", Map.of(request.queryParams("view"), true),
                "uploadDirContents", executorClient.listSideloadDir(nodeId),
                "allBackups",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.BACKUP),
                "allCrawlData",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.CRAWL_DATA),
                "allProcessedData",
                        fileStorageService.getEachFileStorage(nodeId, FileStorageType.PROCESSED_DATA)
        );
    }

    private Object nodeStorageConfModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        return Map.of(
                "tab", Map.of("storage", true),
                "view", Map.of("conf", true),
                "node", nodeConfigurationService.get(nodeId),
                "storagebase", getStorageBaseList(nodeId)
        );
    }


    private Object nodeStorageListModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        String view = request.params("view");

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

    private Object nodeStorageDetailsModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        var fsid = FileStorageId.parse(request.queryParams("fid"));
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
                    request.queryParams("filterDomain"),
                    request.queryParams("afterDomain")
            );
            ret.put("crawlDataFiles", cdFiles);
        }

        return ret;

    }


    private Object nodeConfigModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

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

    private Object updateConfigModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        String act = request.queryParams("act");

        if ("config".equals(act)) {
            var newConfig = new NodeConfiguration(
                    nodeId,
                    request.queryParams("description"),
                    "on".equalsIgnoreCase(request.queryParams("acceptQueries")),
                    "on".equalsIgnoreCase(request.queryParams("autoClean")),
                    "on".equalsIgnoreCase(request.queryParams("includeInPrecession")),
                    "on".equalsIgnoreCase(request.queryParams("keepWarcs")),
                    "on".equalsIgnoreCase(request.queryParams("disabled"))
            );

            nodeConfigurationService.save(newConfig);
        }
        else if ("storage".equals(act)) {
            throw new UnsupportedOperationException();
        }
        else {
            Spark.halt(400);
        }

        return nodeConfigModel(request, response);
    }

    private Object nodeOverviewModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
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
        List<String> services = List.of(ServiceId.Index.serviceName +":"+nodeId, ServiceId.Executor.serviceName +":"+nodeId);
        List<EventLogEntry> events = new ArrayList<>(20);
        for (var service :services) {
            events.addAll(eventLogService.getLastEntriesForService(service, Long.MAX_VALUE, 10));
        }
        events.sort(Comparator.comparing(EventLogEntry::id).reversed());
        return events;
    }

    @SneakyThrows
    public List<IndexNodeStatus> getNodeStatusList() {
        return nodeConfigurationService
                .getAll()
                .stream()
                .sorted(Comparator.comparing(NodeConfiguration::node))
                .map(this::getStatus)
                .toList();
    }

    @SneakyThrows
    public IndexNodeStatus getStatus(NodeConfiguration config) {
        return new IndexNodeStatus(config,
                monitors.isServiceUp(ServiceId.Index, config.node()),
                monitors.isServiceUp(ServiceId.Executor, config.node())
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
