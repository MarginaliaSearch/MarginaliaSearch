package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
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
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;

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

    public void register(Jooby jooby) {
        jooby.get("/actions", this::actionsModel);
        jooby.post("/actions/recalculate-adjacencies-graph", this::calculateAdjacencies);
        jooby.post("/actions/export-all", this::exportAll);
        jooby.post("/actions/reindex-all", this::reindexAll);
        jooby.post("/actions/reprocess-all", this::reprocessAll);
        jooby.post("/actions/recrawl-all", this::recrawlAll);
        jooby.post("/actions/flush-api-caches", this::flushApiCaches);
        jooby.post("/actions/reload-blogs-list", this::reloadBlogsList);
        jooby.post("/actions/update-nsfw-filters", this::updateNsfwFilters);
    }

    private Object actionsModel(Context ctx) throws Exception {
        ControlRendererFactory.Renderer actionsView = rendererFactory.renderer("control/sys/sys-actions");
        ctx.setResponseType(MediaType.html);

        try {
            List<Map<String, Object>> eligibleNodes = new ArrayList<>();
            for (NodeConfiguration node : nodeConfigurationService.getAll()) {
                if (!node.includeInPrecession()) {
                    continue;
                }

                Map<String, Object> properties = new HashMap<>();
                properties.put("node", node);
                properties.put("include", node.includeInPrecession());

                Optional<FileStorageId> storageIdMaybe = fileStorageService.getActiveFileStorages(node.node(), FileStorageType.CRAWL_DATA).stream().findFirst();
                if (storageIdMaybe.isPresent()) {
                    properties.put("storage", fileStorageService.getStorage(storageIdMaybe.get()));
                }

                eligibleNodes.add(properties);
            }

            return actionsView.render(Map.of("precessionNodes", eligibleNodes));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object calculateAdjacencies(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "CALCULATE-ADJACENCIES");

        // This is technically not a partitioned operation, but we execute it at node 1
        // and let the effects be global :-)
        executorClient.calculateAdjacencies(1);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }

    private Object exportAll(Context ctx) throws Exception {
        String exportType = ctx.form("exportType").valueOrNull();

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

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }

    private Object reindexAll(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "REINDEX-ALL");
        controlActorService.start(ControlActor.REINDEX_ALL);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }

    private Object reprocessAll(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "REPROCESS-ALL");
        controlActorService.start(ControlActor.REPROCESS_ALL);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }

    private Object recrawlAll(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "RECRAWL-ALL");
        controlActorService.start(ControlActor.RECRAWL_ALL);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }

    private Object flushApiCaches(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "FLUSH-API-CACHES");
        apiOutbox.sendNotice("FLUSH_CACHES", "");

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }

    private Object reloadBlogsList(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "RELOAD-BLOGS-LIST");
        domainTypes.reloadDomainsList(DomainTypes.Type.BLOG);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }

    private Object updateNsfwFilters(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "UPDATE-NSFW-FILTERS");
        executorClient.updateNsfwFilters();

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToOverview.render(null);
    }
}
