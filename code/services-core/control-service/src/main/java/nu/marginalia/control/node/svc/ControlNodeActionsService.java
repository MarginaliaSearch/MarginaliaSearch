package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.ControlValidationError;
import nu.marginalia.control.RedirectControl;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Singleton
public class ControlNodeActionsService {
    private static final Logger logger = LoggerFactory.getLogger(ControlNodeActionsService.class);
    private final IndexClient indexClient;
    private final RedirectControl redirectControl;
    private final FileStorageService fileStorageService;
    private final ServiceEventLog eventLog;
    private final ExecutorClient executorClient;

    @Inject
    public ControlNodeActionsService(ExecutorClient executorClient,
                                     IndexClient indexClient,
                                     RedirectControl redirectControl,
                                     FileStorageService fileStorageService,
                                     ServiceEventLog eventLog)
    {
        this.executorClient = executorClient;

        this.indexClient = indexClient;
        this.redirectControl = redirectControl;
        this.fileStorageService = fileStorageService;
        this.eventLog = eventLog;

    }

    public void register() {
        Spark.post("/public/nodes/:node/actions/repartition-index", this::triggerRepartition,
                redirectControl.renderRedirectAcknowledgement("Repartitioning", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-encyclopedia", this::sideloadEncyclopedia,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-dirtree", this::sideloadDirtree,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-warc", this::sideloadWarc,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
        Spark.post("/public/nodes/:node/actions/sideload-stackexchange", this::sideloadStackexchange,
                redirectControl.renderRedirectAcknowledgement("Sideloading", "..")
        );
        Spark.post("/public/nodes/:node/actions/download-sample-data", this::downloadSampleData,
                redirectControl.renderRedirectAcknowledgement("Downloading", "..")
        );
        Spark.post("/public/nodes/:id/actions/new-crawl", this::triggerNewCrawl,
                redirectControl.renderRedirectAcknowledgement("Crawling", "..")
        );
        Spark.post("/public/nodes/:id/actions/recrawl", this::triggerAutoRecrawl,
                redirectControl.renderRedirectAcknowledgement("Recrawling", "..")
        );
        Spark.post("/public/nodes/:id/actions/process", this::triggerProcess,
                redirectControl.renderRedirectAcknowledgement("Processing", "..")
        );
        Spark.post("/public/nodes/:id/actions/load", this::triggerLoadSelected,
                redirectControl.renderRedirectAcknowledgement("Loading", "..")
        );
        Spark.post("/public/nodes/:id/actions/restore-backup", this::triggerRestoreBackup,
                redirectControl.renderRedirectAcknowledgement("Restoring", "..")
        );
        Spark.post("/public/nodes/:id/actions/new-crawl-specs", this::createNewSpecsAction,
                redirectControl.renderRedirectAcknowledgement("Creating", "../actions?view=new-crawl")
        );
        Spark.post("/public/nodes/:id/actions/export-db-data", this::exportDbData,
                redirectControl.renderRedirectAcknowledgement("Exporting", "..")
        );
        Spark.post("/public/nodes/:id/actions/export-from-crawl-data", this::exportFromCrawlData,
                redirectControl.renderRedirectAcknowledgement("Exporting", "..")
        );
        Spark.post("/public/nodes/:id/actions/export-sample-data", this::exportSampleData,
                redirectControl.renderRedirectAcknowledgement("Exporting", "..")
        );
    }

    private Object downloadSampleData(Request request, Response response) {
        String set = request.queryParams("sample");

        if (set == null)
            throw new ControlValidationError("No sample specified", "A sample data set must be specified", "..");
        if (!Set.of("sample-s", "sample-m", "sample-l", "sample-xl").contains(set))
            throw new ControlValidationError("Invalid sample specified", "A valid sample data set must be specified", "..");

        executorClient.downloadSampleData(Integer.parseInt(request.params("node")), set);

        logger.info("Downloading sample data set {}", set);

        return "";
    }

    public Object sideloadEncyclopedia(Request request, Response response) {

        String source = request.queryParams("source");
        String baseUrl = request.queryParams("baseUrl");
        int nodeId = Integer.parseInt(request.params("node"));

        if (baseUrl == null)
            throw new ControlValidationError("No baseUrl specified", "A baseUrl must be specified", "..");

        Path sourcePath = parseSourcePath(source);

        eventLog.logEvent("USER-ACTION", "SIDELOAD ENCYCLOPEDIA " + nodeId);

        executorClient.sideloadEncyclopedia(nodeId, sourcePath, baseUrl);

        return "";
    }

    public Object sideloadDirtree(Request request, Response response) {

        final int nodeId = Integer.parseInt(request.params("node"));

        Path sourcePath = parseSourcePath(request.queryParams("source"));

        eventLog.logEvent("USER-ACTION", "SIDELOAD DIRTREE " + nodeId);

        executorClient.sideloadDirtree(nodeId, sourcePath);

        return "";
    }

    public Object sideloadWarc(Request request, Response response) {

        final int nodeId = Integer.parseInt(request.params("node"));
        Path sourcePath = parseSourcePath(request.queryParams("source"));

        eventLog.logEvent("USER-ACTION", "SIDELOAD WARC " + nodeId);

        executorClient.sideloadWarc(nodeId, sourcePath);

        return "";
    }
    public Object sideloadStackexchange(Request request, Response response) {

        final int nodeId = Integer.parseInt(request.params("node"));

        String source = request.queryParams("source");
        if (source == null)
            throw new ControlValidationError("No source specified", "A source file/directory must be specified", "..");
        Path sourcePath = Path.of(source);

        eventLog.logEvent("USER-ACTION", "SIDELOAD STACKEXCHANGE " + nodeId);

        executorClient.sideloadStackexchange(nodeId, sourcePath);

        return "";
    }

    public Object triggerRepartition(Request request, Response response) throws Exception {
        indexClient.triggerRepartition(Integer.parseInt(request.params("node")));

        return "";
    }

    private Object triggerAutoRecrawl(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        var toCrawl = parseSourceFileStorageId(request.queryParams("source"));

        changeActiveStorage(nodeId, FileStorageType.CRAWL_DATA, toCrawl);

        executorClient.triggerRecrawl(
                nodeId,
                toCrawl
        );

        return "";
    }

    private Object triggerNewCrawl(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));

        var toCrawl = parseSourceFileStorageId(request.queryParams("source"));

        changeActiveStorage(nodeId, FileStorageType.CRAWL_SPEC, toCrawl);

        executorClient.triggerCrawl(nodeId, toCrawl);

        return "";
    }

    private Object triggerProcess(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        boolean isAutoload = "on".equalsIgnoreCase(request.queryParams("autoload"));
        var toProcess = parseSourceFileStorageId(request.queryParams("source"));

        changeActiveStorage(nodeId, FileStorageType.PROCESSED_DATA, toProcess);

        if (isAutoload) {
            executorClient.triggerConvertAndLoad(nodeId, toProcess);
        }
        else {
            executorClient.triggerConvert(nodeId, toProcess);
        }

        return "";
    }

    private Object triggerLoadSelected(Request request, Response response) throws SQLException {
        int nodeId = Integer.parseInt(request.params("id"));
        String[] values = request.queryParamsValues("source");

        if (values.length == 0) {
            throw new ControlValidationError("No source specified", "At least one source storage must be specified", "..");
        }

        List<FileStorageId> ids = Arrays.stream(values).map(FileStorageId::parse).toList();

        changeActiveStorage(nodeId, FileStorageType.PROCESSED_DATA, ids.toArray(new FileStorageId[0]));

        executorClient.loadProcessedData(nodeId, ids);

        return "";
    }

    private Object triggerRestoreBackup(Request request, Response response) {
        int nodeId = Integer.parseInt(request.params("id"));

        var toLoad = parseSourceFileStorageId(request.queryParams("source"));

        executorClient.restoreBackup(nodeId, toLoad);

        return "";
    }


    /** Change the active storage for a node of a particular type. */
    private void changeActiveStorage(int nodeId, FileStorageType type, FileStorageId... newActiveStorage) throws SQLException {
        // It is desirable to have the active storage set to reflect which storage was last used
        // for a particular node.

        // Ideally we'd do this in a transaction, but as this is a reminder for the user, and not
        // used for any actual processing, we don't need to be that strict.

        for (var oldActiveStorage : fileStorageService.getActiveFileStorages(nodeId, type)) {
            fileStorageService.setFileStorageState(oldActiveStorage, FileStorageState.UNSET);
        }
        for (var id : newActiveStorage) {
            fileStorageService.setFileStorageState(id, FileStorageState.ACTIVE);
        }
    }


    private Object createNewSpecsAction(Request request, Response response) {
        final String description = request.queryParams("description");
        final String url = request.queryParams("url");
        int nodeId = Integer.parseInt(request.params("id"));

        if (url == null || url.isBlank()) {
            throw new ControlValidationError("No url specified", "A url must be specified", "..");
        }

        executorClient.createCrawlSpecFromDownload(nodeId, description, url);

        return "";
    }

    private Object exportDbData(Request req, Response rsp) {
        executorClient.exportData(Integer.parseInt(req.params("id")));

        return "";
    }

    private Object exportFromCrawlData(Request req, Response rsp) {
        String exportType = req.queryParams("exportType");
        FileStorageId source = parseSourceFileStorageId(req.queryParams("source"));

        switch (exportType) {
            case "atags" -> executorClient.exportAtags(Integer.parseInt(req.params("id")), source);
            case "rss" -> executorClient.exportRssFeeds(Integer.parseInt(req.params("id")), source);
            case "termFreq" -> executorClient.exportTermFrequencies(Integer.parseInt(req.params("id")), source);
            default -> throw new ControlValidationError("No export type specified", "An export type must be specified", "..");
        }

        return "";
    }

    private Object exportSampleData(Request req, Response rsp) {
        FileStorageId source = parseSourceFileStorageId(req.queryParams("source"));
        int size = Integer.parseInt(req.queryParams("size"));
        String name = req.queryParams("name");

        executorClient.exportSampleData(Integer.parseInt(req.params("id")), source, size, name);

        return "";
    }


    private Path parseSourcePath(String source) {
        if (source == null) {
            throw new ControlValidationError("No source specified",
                    "A source file/directory must be specified",
                    "..");
        }
        return Path.of(source);
    }

    private FileStorageId parseSourceFileStorageId(String source) {
        if (source == null) {
            throw new ControlValidationError("No source specified",
                    "A source file storage must be specified",
                    "..");
        }

        try {
            return FileStorageId.parse(source);
        }
        catch (Exception e) { // Typically NumberFormatException
            throw new ControlValidationError("Invalid source specified", "The source file storage is invalid", "..");
        }
    }
}
