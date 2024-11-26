package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.actor.ControlActor;
import nu.marginalia.control.actor.ControlActorService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.executor.client.ExecutorExportClient;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageType;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.*;

public class ControlSysActionsService {
    private final MqOutbox apiOutbox;
    private final DomainTypes domainTypes;
    private final ServiceEventLog eventLog;
    private final ControlRendererFactory rendererFactory;
    private final ControlActorService controlActorService;
    private final NodeConfigurationService nodeConfigurationService;
    private final FileStorageService fileStorageService;
    private final ExecutorClient executorClient;
    private final ExecutorExportClient exportClient;

    @Inject
    public ControlSysActionsService(MessageQueueFactory mqFactory,
                                    DomainTypes domainTypes,
                                    ServiceEventLog eventLog,
                                    ControlRendererFactory rendererFactory,
                                    ControlActorService controlActorService,
                                    NodeConfigurationService nodeConfigurationService,
                                    FileStorageService fileStorageService,
                                    ExecutorClient executorClient, ExecutorExportClient exportClient)
    {
        this.apiOutbox = createApiOutbox(mqFactory);
        this.eventLog = eventLog;
        this.domainTypes = domainTypes;
        this.rendererFactory = rendererFactory;
        this.controlActorService = controlActorService;
        this.nodeConfigurationService = nodeConfigurationService;
        this.fileStorageService = fileStorageService;
        this.executorClient = executorClient;
        this.exportClient = exportClient;
    }

    /** This is a hack to get around the fact that the API service is not a core service
     * and lacks a proper internal API
     */
    private MqOutbox createApiOutbox(MessageQueueFactory mqFactory) {
        String inboxName = ServiceId.Api.serviceName + ":" + "0";
        String outboxName = "pp:"+System.getProperty("service-name", UUID.randomUUID().toString());
        return mqFactory.createOutbox(inboxName, 0, outboxName, 0, UUID.randomUUID());
    }

    public void register() {
        try {
            var actionsView = rendererFactory.renderer("control/sys/sys-actions");

            Spark.get("/actions", this::actionsModel, actionsView::render);
            Spark.post("/actions/recalculate-adjacencies-graph", this::calculateAdjacencies, Redirects.redirectToOverview);
            Spark.post("/actions/export-all", this::exportAll, Redirects.redirectToOverview);
            Spark.post("/actions/reindex-all", this::reindexAll, Redirects.redirectToOverview);
            Spark.post("/actions/reprocess-all", this::reprocessAll, Redirects.redirectToOverview);
            Spark.post("/actions/recrawl-all", this::recrawlAll, Redirects.redirectToOverview);
            Spark.post("/actions/flush-api-caches", this::flushApiCaches, Redirects.redirectToOverview);
            Spark.post("/actions/reload-blogs-list", this::reloadBlogsList, Redirects.redirectToOverview);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object exportAll(Request request, Response response) {
        String exportType = request.queryParams("exportType");

        switch (exportType) {
            case "atags":
                exportClient.exportAllAtags();
                break;
            case "feeds":
                exportClient.exportAllFeeds();
                break;
            default:
                throw new IllegalArgumentException("Unknown export type: " + exportType);
        }

        return "";
    }

    private Object actionsModel(Request request, Response response) {
        try {
            List<Map<String, Object>> eligibleNodes = new ArrayList<>();
            for (var node : nodeConfigurationService.getAll()) {
                if (!node.includeInPrecession()) {
                    continue;
                }

                Map<String, Object> properties = new HashMap<>();
                properties.put("node", node);
                properties.put("include", node.includeInPrecession());

                var storageIdMaybe = fileStorageService.getActiveFileStorages(node.node(), FileStorageType.CRAWL_DATA).stream().findFirst();
                if (storageIdMaybe.isPresent()) {
                    properties.put("storage", fileStorageService.getStorage(storageIdMaybe.get()));
                }

                eligibleNodes.add(properties);
            }

            return Map.of("precessionNodes", eligibleNodes);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object reloadBlogsList(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "RELOAD-BLOGS-LIST");

        domainTypes.reloadDomainsList(DomainTypes.Type.BLOG);

        return "";
    }

    public Object flushApiCaches(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "FLUSH-API-CACHES");
        apiOutbox.sendNotice("FLUSH_CACHES", "");

        return "";
    }

    public Object calculateAdjacencies(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "CALCULATE-ADJACENCIES");

        // This is technically not a partitioned operation, but we execute it at node 1
        // and let the effects be global :-)

        executorClient.calculateAdjacencies(1);

        return "";
    }

    public Object reindexAll(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "REINDEX-ALL");

        controlActorService.start(ControlActor.REINDEX_ALL);

        return "";
    }

    public Object reprocessAll(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "REPROCESS-ALL");

        controlActorService.start(ControlActor.REPROCESS_ALL);

        return "";
    }

    public Object recrawlAll(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "RECRAWL-ALL");

        controlActorService.start(ControlActor.RECRAWL_ALL);

        return "";
    }
}
