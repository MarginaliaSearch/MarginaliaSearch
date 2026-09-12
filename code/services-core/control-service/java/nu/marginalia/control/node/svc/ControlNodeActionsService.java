package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
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
        jooby.post("/nodes/{node}/actions/repartition-index", ctx -> redirectControl.renderRedirectAcknowledgement("Repartitioning", "..").render(triggerRepartition(ctx)));
        jooby.post("/nodes/{node}/actions/sideload-encyclopedia", ctx -> redirectControl.renderRedirectAcknowledgement("Sideloading", "..").render(sideloadEncyclopedia(ctx)));
        jooby.post("/nodes/{node}/actions/sideload-dirtree", ctx -> redirectControl.renderRedirectAcknowledgement("Sideloading", "..").render(sideloadDirtree(ctx)));
        jooby.post("/nodes/{node}/actions/sideload-reddit", ctx -> redirectControl.renderRedirectAcknowledgement("Sideloading", "..").render(sideloadReddit(ctx)));
        jooby.post("/nodes/{node}/actions/sideload-warc", ctx -> redirectControl.renderRedirectAcknowledgement("Sideloading", "..").render(sideloadWarc(ctx)));
        jooby.post("/nodes/{node}/actions/sideload-stackexchange", ctx -> redirectControl.renderRedirectAcknowledgement("Sideloading", "..").render(sideloadStackexchange(ctx)));
        jooby.post("/nodes/{node}/actions/export-segmentation", ctx -> redirectControl.renderRedirectAcknowledgement("Exporting", "..").render(exportSegmentationModel(ctx)));
        jooby.post("/nodes/{node}/actions/download-sample-data", ctx -> redirectControl.renderRedirectAcknowledgement("Downloading", "..").render(downloadSampleData(ctx)));
        jooby.post("/nodes/{id}/actions/new-crawl", ctx -> redirectControl.renderRedirectAcknowledgement("Crawling", "..").render(triggerCrawl(ctx)));
        jooby.post("/nodes/{id}/actions/recrawl-single-domain", ctx -> redirectControl.renderRedirectAcknowledgement("Recrawling", "..").render(triggerSingleDomainRecrawl(ctx)));
        jooby.post("/nodes/{id}/actions/process", ctx -> redirectControl.renderRedirectAcknowledgement("Processing", "..").render(triggerProcess(ctx)));
        jooby.post("/nodes/{id}/actions/load", ctx -> redirectControl.renderRedirectAcknowledgement("Loading", "..").render(triggerLoadSelected(ctx)));
        jooby.post("/nodes/{id}/actions/restore-backup", ctx -> redirectControl.renderRedirectAcknowledgement("Restoring", "..").render(triggerRestoreBackup(ctx)));
        jooby.post("/nodes/{id}/actions/export-db-data", ctx -> redirectControl.renderRedirectAcknowledgement("Exporting", "..").render(exportDbData(ctx)));
        jooby.post("/nodes/{id}/actions/export-from-crawl-data", ctx -> redirectControl.renderRedirectAcknowledgement("Exporting", "..").render(exportFromCrawlData(ctx)));
        jooby.post("/nodes/{id}/actions/export-sample-data", ctx -> redirectControl.renderRedirectAcknowledgement("Exporting", "..").render(exportSampleData(ctx)));
        jooby.post("/nodes/{id}/actions/export-dom-sample-data", ctx -> redirectControl.renderRedirectAcknowledgement("Exporting", "..").render(exportDomSampleData(ctx)));
    }

    private Object downloadSampleData(Context ctx) {
        String set = ctx.lookup("sample", QUERY, FORM).valueOrNull();

        if (set == null)
            throw new ControlValidationError("No sample specified", "A sample data set must be specified", "..");
        if (!Set.of("sample-s", "sample-m", "sample-l", "sample-xl").contains(set))
            throw new ControlValidationError("Invalid sample specified", "A valid sample data set must be specified", "..");

        executorClient.downloadSampleData(Integer.parseInt(ctx.path("node").value()), set);

        logger.info("Downloading sample data set {}", set);

        return "";
    }

    public Object sideloadEncyclopedia(Context ctx) {

        String source = ctx.lookup("source", QUERY, FORM).valueOrNull();
        String baseUrl = ctx.lookup("baseUrl", QUERY, FORM).valueOrNull();
        int nodeId = Integer.parseInt(ctx.path("node").value());

        if (baseUrl == null)
            throw new ControlValidationError("No baseUrl specified", "A baseUrl must be specified", "..");

        Path sourcePath = parseSourcePath(source);

        eventLog.logEvent("USER-ACTION", "SIDELOAD ENCYCLOPEDIA " + nodeId);

        sideloadClient.sideloadEncyclopedia(nodeId, sourcePath, baseUrl);

        return "";
    }

    public Object sideloadDirtree(Context ctx) {

        final int nodeId = Integer.parseInt(ctx.path("node").value());

        Path sourcePath = parseSourcePath(ctx.lookup("source", QUERY, FORM).valueOrNull());

        eventLog.logEvent("USER-ACTION", "SIDELOAD DIRTREE " + nodeId);

        sideloadClient.sideloadDirtree(nodeId, sourcePath);

        return "";
    }
    public Object sideloadReddit(Context ctx) {

        final int nodeId = Integer.parseInt(ctx.path("node").value());

        Path sourcePath = parseSourcePath(ctx.lookup("source", QUERY, FORM).valueOrNull());

        eventLog.logEvent("USER-ACTION", "SIDELOAD REDDIT " + nodeId);

        sideloadClient.sideloadReddit(nodeId, sourcePath);

        return "";
    }
    public Object sideloadWarc(Context ctx) {

        final int nodeId = Integer.parseInt(ctx.path("node").value());
        Path sourcePath = parseSourcePath(ctx.lookup("source", QUERY, FORM).valueOrNull());

        eventLog.logEvent("USER-ACTION", "SIDELOAD WARC " + nodeId);

        sideloadClient.sideloadWarc(nodeId, sourcePath);

        return "";
    }
    public Object sideloadStackexchange(Context ctx) {

        final int nodeId = Integer.parseInt(ctx.path("node").value());

        String source = ctx.lookup("source", QUERY, FORM).valueOrNull();
        if (source == null)
            throw new ControlValidationError("No source specified", "A source file/directory must be specified", "..");
        Path sourcePath = Path.of(source);

        eventLog.logEvent("USER-ACTION", "SIDELOAD STACKEXCHANGE " + nodeId);

        sideloadClient.sideloadStackexchange(nodeId, sourcePath);

        return "";
    }

    public Object triggerRepartition(Context ctx) throws Exception {
        indexMqClient.triggerRepartition(Integer.parseInt(ctx.path("node").value()));

        return "";
    }

    private Object triggerCrawl(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        var toCrawl = parseSourceFileStorageId(ctx.lookup("source", QUERY, FORM).valueOrNull());

        changeActiveStorage(nodeId, FileStorageType.CRAWL_DATA, toCrawl);

        crawlClient.triggerCrawl(
                nodeId,
                toCrawl
        );

        return "";
    }

    private Object triggerSingleDomainRecrawl(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        var toCrawl = parseSourceFileStorageId(ctx.lookup("source", QUERY, FORM).valueOrNull());
        var targetDomainName = Objects.requireNonNull(ctx.lookup("targetDomainName", QUERY, FORM).valueOrNull());

        crawlClient.triggerRecrawlSingleDomain(
                nodeId,
                toCrawl,
                targetDomainName
        );

        return "";
    }

    private Object triggerProcess(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        boolean isAutoload = "on".equalsIgnoreCase(ctx.lookup("autoload", QUERY, FORM).valueOrNull());
        var toProcess = parseSourceFileStorageId(ctx.lookup("source", QUERY, FORM).valueOrNull());

        changeActiveStorage(nodeId, FileStorageType.PROCESSED_DATA, toProcess);

        if (isAutoload) {
            crawlClient.triggerConvertAndLoad(nodeId, toProcess);
        }
        else {
            crawlClient.triggerConvert(nodeId, toProcess);
        }

        return "";
    }

    private Object triggerLoadSelected(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        String[] values = ctx.lookup("source", QUERY, FORM).toList().toArray(String[]::new);

        if (values.length == 0) {
            throw new ControlValidationError("No source specified", "At least one source storage must be specified", "..");
        }

        List<FileStorageId> ids = Arrays.stream(values).map(FileStorageId::parse).toList();

        changeActiveStorage(nodeId, FileStorageType.PROCESSED_DATA, ids.toArray(new FileStorageId[0]));

        crawlClient.loadProcessedData(nodeId, ids);

        return "";
    }

    private Object triggerRestoreBackup(Context ctx) {
        int nodeId = Integer.parseInt(ctx.path("id").value());

        var toLoad = parseSourceFileStorageId(ctx.lookup("source", QUERY, FORM).valueOrNull());

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

    private Object exportDbData(Context ctx) {
        exportClient.exportData(Integer.parseInt(ctx.path("id").value()));

        return "";
    }

    private Object exportSegmentationModel(Context ctx) {
        exportClient.exportSegmentationModel(
                Integer.parseInt(ctx.path("node").value()),
                ctx.lookup("source", QUERY, FORM).valueOrNull());

        return "";
    }

    private Object exportFromCrawlData(Context ctx) throws Exception {
        String exportType = ctx.lookup("exportType", QUERY, FORM).valueOrNull();
        FileStorageId source = parseSourceFileStorageId(ctx.lookup("source", QUERY, FORM).valueOrNull());

        switch (exportType) {
            case "atags" -> exportClient.exportAtags(Integer.parseInt(ctx.path("id").value()), source);
            case "rss" -> exportClient.exportRssFeeds(Integer.parseInt(ctx.path("id").value()), source);
            case "termFreq" -> exportClient.exportTermFrequencies(Integer.parseInt(ctx.path("id").value()), source);
            default -> throw new ControlValidationError("No export type specified", "An export type must be specified", "..");
        }

        return "";
    }

    private Object exportSampleData(Context ctx) {
        FileStorageId source = parseSourceFileStorageId(ctx.lookup("source", QUERY, FORM).valueOrNull());
        int size = Integer.parseInt(ctx.lookup("size", QUERY, FORM).valueOrNull());
        String ctFilter = ctx.lookup("ctFilter", QUERY, FORM).valueOrNull();
        String name = ctx.lookup("name", QUERY, FORM).valueOrNull();

        exportClient.exportSampleData(Integer.parseInt(ctx.path("id").value()), source, size, ctFilter, name);

        return "";
    }

    private Object exportDomSampleData(Context ctx) throws Exception {
        // Sanity check to ensure we run this on the right node,
        // should be ensured by the UI as well.
        if (1 != Integer.parseInt(ctx.path("id").value()))
            throw new IllegalArgumentException("Must only be run on node 1");

        exportClient.exportDomSampleData();

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
