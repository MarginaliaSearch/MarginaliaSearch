package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.client.Context;
import nu.marginalia.client.ServiceMonitors;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.node.model.*;
import nu.marginalia.control.sys.model.EventLogEntry;
import nu.marginalia.control.sys.svc.EventLogService;
import nu.marginalia.control.sys.svc.HeartbeatService;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.executor.model.load.LoadParameters;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.service.id.ServiceId;
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
    private final RendererFactory rendererFactory;
    private final EventLogService eventLogService;
    private final HeartbeatService heartbeatService;
    private final ExecutorClient executorClient;
    private final HikariDataSource dataSource;
    private final ServiceMonitors monitors;
    private final NodeConfigurationService nodeConfigurationService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ControlNodeService(
            FileStorageService fileStorageService,
            RendererFactory rendererFactory,
            EventLogService eventLogService,
            HeartbeatService heartbeatService,
            ExecutorClient executorClient,
            HikariDataSource dataSource,
            ServiceMonitors monitors, NodeConfigurationService nodeConfigurationService)
    {
        this.fileStorageService = fileStorageService;
        this.rendererFactory = rendererFactory;
        this.eventLogService = eventLogService;
        this.heartbeatService = heartbeatService;
        this.executorClient = executorClient;
        this.dataSource = dataSource;
        this.monitors = monitors;
        this.nodeConfigurationService = nodeConfigurationService;
    }

    public void register() throws IOException {
        var nodeListRenderer = rendererFactory.renderer("control/node/nodes-list");
        var overviewRenderer = rendererFactory.renderer("control/node/node-overview");
        var actionsRenderer = rendererFactory.renderer("control/node/node-actions");
        var actorsRenderer = rendererFactory.renderer("control/node/node-actors");
        var storageConfRenderer = rendererFactory.renderer("control/node/node-storage-conf");
        var storageListRenderer = rendererFactory.renderer("control/node/node-storage-list");
        var storageDetailsRenderer = rendererFactory.renderer("control/node/node-storage-details");
        var configRenderer = rendererFactory.renderer("control/node/node-config");

        var newSpecsFormRenderer = rendererFactory.renderer("control/node/node-new-specs-form");

        Spark.get("/public/nodes", this::nodeListModel, nodeListRenderer::render);
        Spark.get("/public/nodes/:id", this::nodeOverviewModel, overviewRenderer::render);
        Spark.get("/public/nodes/:id/", this::nodeOverviewModel, overviewRenderer::render);
        Spark.get("/public/nodes/:id/actors", this::nodeActorsModel, actorsRenderer::render);
        Spark.get("/public/nodes/:id/actions", this::nodeActionsModel, actionsRenderer::render);
        Spark.get("/public/nodes/:id/storage/", this::nodeStorageConfModel, storageConfRenderer::render);
        Spark.get("/public/nodes/:id/storage/conf", this::nodeStorageConfModel, storageConfRenderer::render);
        Spark.get("/public/nodes/:id/storage/details", this::nodeStorageDetailsModel, storageDetailsRenderer::render);

        Spark.get("/public/nodes/:id/storage/new-specs", this::newSpecsModel, newSpecsFormRenderer::render);
        Spark.post("/public/nodes/:id/storage/new-specs", this::createNewSpecsAction);

        Spark.get("/public/nodes/:id/storage/:view", this::nodeStorageListModel, storageListRenderer::render);

        Spark.get("/public/nodes/:id/configuration", this::nodeConfigModel, configRenderer::render);
        Spark.post("/public/nodes/:id/configuration", this::updateConfigModel, configRenderer::render);

        Spark.post("/public/nodes/:id/storage/recrawl-auto", this::triggerAutoRecrawl);
        Spark.post("/public/nodes/:id/storage/process-auto", this::triggerAutoProcess);
        Spark.post("/public/nodes/:id/storage/load-selected", this::triggerLoadSelected);
        Spark.post("/public/nodes/:id/storage/crawl/:fid", this::triggerCrawl);
        Spark.post("/public/nodes/:id/storage/backup-restore/:fid", this::triggerRestoreBackup);

        Spark.post("/public/nodes/:id/storage/:fid/delete", this::deleteFileStorage);
        Spark.post("/public/nodes/:id/storage/:fid/enable", this::enableFileStorage);
        Spark.post("/public/nodes/:id/storage/:fid/disable", this::disableFileStorage);
        Spark.get("/public/nodes/:id/storage/:fid/transfer", this::downloadFileFromStorage);


        Spark.post("/public/nodes/:id/fsms/:fsm/start", this::startFsm);
        Spark.post("/public/nodes/:id/fsms/:fsm/stop", this::stopFsm);
    }

    public Object startFsm(Request req, Response rsp) throws Exception {
        executorClient.startFsm(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fsm").toUpperCase());

        return redirectToOverview(req);
    }

    public Object stopFsm(Request req, Response rsp) throws Exception {
        executorClient.stopFsm(Context.fromRequest(req), Integer.parseInt(req.params("node")), req.params("fsm").toUpperCase());

        return redirectToOverview(req);
    }
    private Object nodeListModel(Request request, Response response) throws SQLException {
        var configs = nodeConfigurationService.getAll();

        int nextId = configs.stream().mapToInt(NodeConfiguration::node).map(i -> i+1).max().orElse(1);

        return Map.of("nodes", nodeConfigurationService.getAll(),
                      "nextNodeId", nextId);
    }

    private Object triggerCrawl(Request request, Response response) {
        int nodeId = Integer.parseInt(request.params("id"));

        executorClient.triggerCrawl(Context.fromRequest(request), nodeId, request.params("fid"));

        return redirectToOverview(request);
    }

    private Object triggerRestoreBackup(Request request, Response response) {
        int nodeId = Integer.parseInt(request.params("id"));

        executorClient.restoreBackup(Context.fromRequest(request), nodeId, request.params("fid"));

        return redirectToOverview(request);
    }
    @SneakyThrows
    public String redirectToOverview(int nodeId) {
        return new Redirects.HtmlRedirect("/nodes/"+nodeId).render(null);
    }

    @SneakyThrows
    public String redirectToOverview(Request request) {
        return redirectToOverview(Integer.parseInt(request.params("id")));
    }

    private Object createNewSpecsAction(Request request, Response response) {
        final String description = request.queryParams("description");
        final String url = request.queryParams("url");
        int nodeId = Integer.parseInt(request.params("id"));

        executorClient.createCrawlSpecFromDownload(Context.fromRequest(request), nodeId, description, url);

        return redirectToOverview(request);
    }

    private Object newSpecsModel(Request request, Response response) {
        int nodeId = Integer.parseInt(request.params("id"));

        return Map.of(
                "node", new IndexNode(nodeId),
                "view", Map.of("specs", true)
        );
    }

    private Object triggerAutoRecrawl(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        var toCrawl = fileStorageService.getOnlyActiveFileStorage(nodeId, FileStorageType.CRAWL_DATA);

        executorClient.triggerRecrawl(
                Context.fromRequest(request),
                nodeId,
                toCrawl.orElseThrow(AssertionError::new)
        );

        return redirectToOverview(request);
    }

    private Object triggerAutoProcess(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        var toConvert = fileStorageService.getOnlyActiveFileStorage(nodeId, FileStorageType.CRAWL_DATA);

        executorClient.triggerConvertAndLoad(Context.fromRequest(request),
                nodeId,
                toConvert.orElseThrow(AssertionError::new));

        return redirectToOverview(request);
    }

    private Object triggerLoadSelected(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        var toLoadStorages = fileStorageService.getActiveFileStorages(nodeId, FileStorageType.PROCESSED_DATA);

        executorClient.loadProcessedData(Context.fromRequest(request),
                nodeId,
                new LoadParameters(toLoadStorages)
        );

        return redirectToOverview(request);
    }

    private Object deleteFileStorage(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        int fileId = Integer.parseInt(request.params("fid"));

        fileStorageService.flagFileForDeletion(new FileStorageId(fileId));

        return redirectToOverview(request);
    }

    private Object enableFileStorage(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        FileStorageId fileId = new FileStorageId(Integer.parseInt(request.params("fid")));

        var storage = fileStorageService.getStorage(fileId);
        if (storage.type() == FileStorageType.CRAWL_DATA) {
            fileStorageService.disableFileStorageOfType(nodeId, storage.type());
        }

        fileStorageService.enableFileStorage(fileId);

        return "";
    }

    private Object disableFileStorage(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        int fileId = Integer.parseInt(request.params("fid"));

        fileStorageService.disableFileStorage(new FileStorageId(fileId));

        return "";
    }

    private Object nodeActorsModel(Request request, Response response) {
        int nodeId = Integer.parseInt(request.params("id"));

        return Map.of(
                "node", new IndexNode(nodeId),
                "actors", executorClient.getActorStates(Context.fromRequest(request), nodeId).states()
        );
    }

    private Object nodeActionsModel(Request request, Response response) {
        int nodeId = Integer.parseInt(request.params("id"));

        return Map.of(
                "node", new IndexNode(nodeId)
        );
    }

    private Object nodeStorageConfModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        return Map.of(
                "view", Map.of("conf", true),
                "node", new IndexNode(nodeId),
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
            case "specs" -> FileStorageType.CRAWL_SPEC;
            default -> throw new IllegalArgumentException(view);
        };

        return Map.of(
                "view", Map.of(view, true),
                "node", new IndexNode(nodeId),
                "storage", makeFileStorageBaseWithStorage(getFileStorageIds(type, nodeId))
        );
    }

    private Object nodeStorageDetailsModel(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        var storage = getFileStorageWithRelatedEntries(Context.fromRequest(request), nodeId, FileStorageId.parse(request.queryParams("fid")));

        String view = switch(storage.type()) {
            case BACKUP -> "backup";
            case CRAWL_DATA -> "crawl";
            case CRAWL_SPEC -> "specs";
            case PROCESSED_DATA -> "processed";
            default -> throw new IllegalStateException(storage.type().toString());
        };

        return Map.of(
                "view", Map.of(view, true),
                "node", new IndexNode(nodeId),
                "storage", storage);
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
                "node", new IndexNode(nodeId),
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
        return Map.of(
                "node", new IndexNode(nodeId),
                "status", getStatus(config),
                "events", getEvents(nodeId),
                "processes", heartbeatService.getProcessHeartbeatsForNode(nodeId),
                "jobs", heartbeatService.getTaskHeartbeatsForNode(nodeId)
                );
    }

    public Object downloadFileFromStorage(Request request, Response response) throws IOException {
        int nodeId = Integer.parseInt(request.params("id"));
        var fileStorageId = FileStorageId.parse(request.params("fid"));

        String path = request.queryParams("path");

        response.header("content-disposition", "attachment; filename=\""+path+"\"");

        if (path.endsWith(".txt") || path.endsWith(".log"))
            response.type("text/plain");
        else
            response.type("application/octet-stream");

        executorClient.transferFile(Context.fromRequest(request), nodeId, fileStorageId, path, response.raw().getOutputStream());

        return "";
    }

    private Object getStorageBaseList(int nodeId) throws SQLException {
        List<FileStorageBase> bases = new ArrayList<>();

        for (var type : FileStorageBaseType.values()) {
            var base = fileStorageService.getStorageBase(type, nodeId);
            bases.add(Objects.requireNonNullElseGet(base,
                    () -> new FileStorageBase(new FileStorageBaseId(-1), type, "MISSING", "MISSING"))
            );
        }

        return bases;
    }

    private List<EventLogEntry> getEvents(int nodeId) {
        List<String> services = List.of(ServiceId.Index.name+":"+nodeId, ServiceId.Executor.name+":"+nodeId);
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
        Map<FileStorageBaseId, List<FileStorageWithActions>> fileStoragByBaseId = new HashMap<>();

        for (var id : storageIds) {
            var storage = fileStorageService.getStorage(id);
            fileStorageBaseByBaseId.computeIfAbsent(storage.base().id(), k -> storage.base());
            fileStoragByBaseId.computeIfAbsent(storage.base().id(), k -> new ArrayList<>()).add(new FileStorageWithActions(storage));
        }

        List<FileStorageBaseWithStorage> result = new ArrayList<>();
        for (var baseId : fileStorageBaseByBaseId.keySet()) {
            result.add(new FileStorageBaseWithStorage(fileStorageBaseByBaseId.get(baseId),
                    fileStoragByBaseId.get(baseId)

            ));
        }

        return result;
    }


    public FileStorageWithRelatedEntries getFileStorageWithRelatedEntries(
            Context context,
            int node,
            FileStorageId fileId
    ) throws SQLException {
        var storage = fileStorageService.getStorage(fileId);
        var related = getRelatedEntries(fileId);

        List<FileStorageFileModel> files = new ArrayList<>();

        for (var execFile : executorClient.listFileStorage(context, node, fileId).files()) {
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
