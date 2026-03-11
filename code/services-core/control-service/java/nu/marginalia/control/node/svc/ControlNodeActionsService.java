package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlValidationError;
import nu.marginalia.control.RedirectControl;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.executor.client.ExecutorCrawlClient;
import nu.marginalia.executor.client.ExecutorExportClient;
import nu.marginalia.executor.client.ExecutorSideloadClient;
import nu.marginalia.index.api.IndexMqClient;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Singleton
public class ControlNodeActionsService {
    private static final Logger logger = LoggerFactory.getLogger(ControlNodeActionsService.class);
    private final IndexMqClient indexMqClient;
    private final RedirectControl redirectControl;
    private final FileStorageService fileStorageService;
    private final ServiceEventLog eventLog;
    private final ExecutorClient executorClient;
    private final ExecutorCrawlClient crawlClient;
    private final ExecutorSideloadClient sideloadClient;
    private final ExecutorExportClient exportClient;

    @Inject
    public ControlNodeActionsService(ExecutorClient executorClient,
                                     IndexMqClient indexMqClient,
                                     RedirectControl redirectControl,
                                     FileStorageService fileStorageService,
                                     ServiceEventLog eventLog, ExecutorCrawlClient crawlClient, ExecutorSideloadClient sideloadClient, ExecutorExportClient exportClient)
    {
        this.executorClient = executorClient;

        this.indexMqClient = indexMqClient;
        this.redirectControl = redirectControl;
        this.fileStorageService = fileStorageService;
        this.eventLog = eventLog;

        this.crawlClient = crawlClient;
        this.sideloadClient = sideloadClient;
        this.exportClient = exportClient;
    }

    public void register(Jooby jooby) {
        jooby.post("/nodes/{node}/actions/repartition-index", this::triggerRepartition);
        jooby.post("/nodes/{node}/actions/sideload-encyclopedia", this::sideloadEncyclopedia);
        jooby.post("/nodes/{node}/actions/sideload-dirtree", this::sideloadDirtree);
        jooby.post("/nodes/{node}/actions/sideload-reddit", this::sideloadReddit);
        jooby.post("/nodes/{node}/actions/sideload-warc", this::sideloadWarc);
        jooby.post("/nodes/{node}/actions/sideload-stackexchange", this::sideloadStackexchange);
        jooby.post("/nodes/{node}/actions/export-segmentation", this::exportSegmentationModel);
        jooby.post("/nodes/{node}/actions/download-sample-data", this::downloadSampleData);
        jooby.post("/nodes/{id}/actions/new-crawl", this::triggerCrawl);
        jooby.post("/nodes/{id}/actions/recrawl-single-domain", this::triggerSingleDomainRecrawl);
        jooby.post("/nodes/{id}/actions/process", this::triggerProcess);
        jooby.post("/nodes/{id}/actions/load", this::triggerLoadSelected);
        jooby.post("/nodes/{id}/actions/restore-backup", this::triggerRestoreBackup);
        jooby.post("/nodes/{id}/actions/export-db-data", this::exportDbData);
        jooby.post("/nodes/{id}/actions/export-from-crawl-data", this::exportFromCrawlData);
        jooby.post("/nodes/{id}/actions/export-sample-data", this::exportSampleData);
        jooby.post("/nodes/{id}/actions/export-dom-sample-data", this::exportDomSampleData);
    }

    private Object triggerRepartition(Context ctx) throws Exception {
        indexMqClient.triggerRepartition(Integer.parseInt(ctx.path("node").value()));

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Repartitioning", "..");
    }

    private Object sideloadEncyclopedia(Context ctx) throws Exception {
        String source = ctx.form("source").valueOrNull();
        String baseUrl = ctx.form("baseUrl").valueOrNull();
        int nodeId = Integer.parseInt(ctx.path("node").value());

        if (baseUrl == null)
            throw new ControlValidationError("No baseUrl specified", "A baseUrl must be specified", "..");

        Path sourcePath = parseSourcePath(source);

        eventLog.logEvent("USER-ACTION", "SIDELOAD ENCYCLOPEDIA " + nodeId);

        sideloadClient.sideloadEncyclopedia(nodeId, sourcePath, baseUrl);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Sideloading", "..");
    }

    private Object sideloadDirtree(Context ctx) throws Exception {
        final int nodeId = Integer.parseInt(ctx.path("node").value());

        Path sourcePath = parseSourcePath(ctx.form("source").valueOrNull());

        eventLog.logEvent("USER-ACTION", "SIDELOAD DIRTREE " + nodeId);

        sideloadClient.sideloadDirtree(nodeId, sourcePath);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Sideloading", "..");
    }

    private Object sideloadReddit(Context ctx) throws Exception {
        final int nodeId = Integer.parseInt(ctx.path("node").value());

        Path sourcePath = parseSourcePath(ctx.form("source").valueOrNull());

        eventLog.logEvent("USER-ACTION", "SIDELOAD REDDIT " + nodeId);

        sideloadClient.sideloadReddit(nodeId, sourcePath);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Sideloading", "..");
    }

    private Object sideloadWarc(Context ctx) throws Exception {
        final int nodeId = Integer.parseInt(ctx.path("node").value());
        Path sourcePath = parseSourcePath(ctx.form("source").valueOrNull());

        eventLog.logEvent("USER-ACTION", "SIDELOAD WARC " + nodeId);

        sideloadClient.sideloadWarc(nodeId, sourcePath);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Sideloading", "..");
    }

    private Object sideloadStackexchange(Context ctx) throws Exception {
        final int nodeId = Integer.parseInt(ctx.path("node").value());

        String source = ctx.form("source").valueOrNull();
        if (source == null)
            throw new ControlValidationError("No source specified", "A source file/directory must be specified", "..");
        Path sourcePath = Path.of(source);

        eventLog.logEvent("USER-ACTION", "SIDELOAD STACKEXCHANGE " + nodeId);

        sideloadClient.sideloadStackexchange(nodeId, sourcePath);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Sideloading", "..");
    }

    private Object exportSegmentationModel(Context ctx) throws Exception {
        exportClient.exportSegmentationModel(
                Integer.parseInt(ctx.path("node").value()),
                ctx.form("source").valueOrNull());

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Exporting", "..");
    }

    private Object downloadSampleData(Context ctx) throws Exception {
        String set = ctx.form("sample").valueOrNull();

        if (set == null)
            throw new ControlValidationError("No sample specified", "A sample data set must be specified", "..");
        if (!Set.of("sample-s", "sample-m", "sample-l", "sample-xl").contains(set))
            throw new ControlValidationError("Invalid sample specified", "A valid sample data set must be specified", "..");

        executorClient.downloadSampleData(Integer.parseInt(ctx.path("node").value()), set);

        logger.info("Downloading sample data set {}", set);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Downloading", "..");
    }

    private Object triggerCrawl(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        FileStorageId toCrawl = parseSourceFileStorageId(ctx.form("source").valueOrNull());

        changeActiveStorage(nodeId, FileStorageType.CRAWL_DATA, toCrawl);

        crawlClient.triggerCrawl(nodeId, toCrawl);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Crawling", "..");
    }

    private Object triggerSingleDomainRecrawl(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        FileStorageId toCrawl = parseSourceFileStorageId(ctx.form("source").valueOrNull());
        String targetDomainName = Objects.requireNonNull(ctx.form("targetDomainName").valueOrNull());

        crawlClient.triggerRecrawlSingleDomain(nodeId, toCrawl, targetDomainName);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Recrawling", "..");
    }

    private Object triggerProcess(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        boolean isAutoload = "on".equalsIgnoreCase(ctx.form("autoload").valueOrNull());
        FileStorageId toProcess = parseSourceFileStorageId(ctx.form("source").valueOrNull());

        changeActiveStorage(nodeId, FileStorageType.PROCESSED_DATA, toProcess);

        if (isAutoload) {
            crawlClient.triggerConvertAndLoad(nodeId, toProcess);
        }
        else {
            crawlClient.triggerConvert(nodeId, toProcess);
        }

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Processing", "..");
    }

    private Object triggerLoadSelected(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        List<String> values = ctx.form("source").toList();

        if (values.isEmpty()) {
            throw new ControlValidationError("No source specified", "At least one source storage must be specified", "..");
        }

        List<FileStorageId> ids = values.stream().map(FileStorageId::parse).toList();

        changeActiveStorage(nodeId, FileStorageType.PROCESSED_DATA, ids.toArray(new FileStorageId[0]));

        crawlClient.loadProcessedData(nodeId, ids);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Loading", "..");
    }

    private Object triggerRestoreBackup(Context ctx) throws Exception {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        FileStorageId toLoad = parseSourceFileStorageId(ctx.form("source").valueOrNull());

        executorClient.restoreBackup(nodeId, toLoad);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Restoring", "..");
    }

    private Object exportDbData(Context ctx) throws Exception {
        exportClient.exportData(Integer.parseInt(ctx.path("id").value()));

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Exporting", "..");
    }

    private Object exportFromCrawlData(Context ctx) throws Exception {
        String exportType = ctx.form("exportType").valueOrNull();
        FileStorageId source = parseSourceFileStorageId(ctx.form("source").valueOrNull());

        switch (exportType) {
            case "atags" -> exportClient.exportAtags(Integer.parseInt(ctx.path("id").value()), source);
            case "rss" -> exportClient.exportRssFeeds(Integer.parseInt(ctx.path("id").value()), source);
            case "termFreq" -> exportClient.exportTermFrequencies(Integer.parseInt(ctx.path("id").value()), source);
            default -> throw new ControlValidationError("No export type specified", "An export type must be specified", "..");
        }

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Exporting", "..");
    }

    private Object exportSampleData(Context ctx) throws Exception {
        FileStorageId source = parseSourceFileStorageId(ctx.form("source").valueOrNull());
        int size = Integer.parseInt(ctx.form("size").valueOrNull());
        String ctFilter = ctx.form("ctFilter").valueOrNull();
        String name = ctx.form("name").valueOrNull();

        exportClient.exportSampleData(Integer.parseInt(ctx.path("id").value()), source, size, ctFilter, name);

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Exporting", "..");
    }

    private Object exportDomSampleData(Context ctx) throws Exception {
        // Sanity check to ensure we run this on the right node,
        // should be ensured by the UI as well.
        if (1 != Integer.parseInt(ctx.path("id").value()))
            throw new IllegalArgumentException("Must only be run on node 1");

        exportClient.exportDomSampleData();

        ctx.setResponseType(MediaType.html);
        return redirectControl.justRender("Exporting", "..");
    }

    /** Change the active storage for a node of a particular type. */
    private void changeActiveStorage(int nodeId, FileStorageType type, FileStorageId... newActiveStorage) throws SQLException {
        // It is desirable to have the active storage set to reflect which storage was last used
        // for a particular node.

        // Ideally we'd do this in a transaction, but as this is a reminder for the user, and not
        // used for any actual processing, we don't need to be that strict.

        for (FileStorageId oldActiveStorage : fileStorageService.getActiveFileStorages(nodeId, type)) {
            fileStorageService.setFileStorageState(oldActiveStorage, FileStorageState.UNSET);
        }
        for (FileStorageId id : newActiveStorage) {
            fileStorageService.setFileStorageState(id, FileStorageState.ACTIVE);
        }
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
