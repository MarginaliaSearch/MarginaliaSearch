package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.ControlValidationError;
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
import nu.marginalia.storage.model.FileStorageType;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

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
        try {
            var actionsView = rendererFactory.renderer("control/sys/sys-actions");

            jooby.get("/actions", ctx -> actionsView.render(actionsModel(ctx)));
            jooby.post("/actions/recalculate-adjacencies-graph", ctx -> Redirects.redirectToOverview.render(calculateAdjacencies(ctx)));
            jooby.post("/actions/discover-new-domains", ctx -> Redirects.redirectToOverview.render(discoverNewDomains(ctx)));
            jooby.post("/actions/export-all", ctx -> Redirects.redirectToOverview.render(exportAll(ctx)));
            jooby.post("/actions/reindex-all", ctx -> Redirects.redirectToOverview.render(reindexAll(ctx)));
            jooby.post("/actions/reprocess-all", ctx -> Redirects.redirectToOverview.render(reprocessAll(ctx)));
            jooby.post("/actions/recrawl-all", ctx -> Redirects.redirectToOverview.render(recrawlAll(ctx)));
            jooby.post("/actions/flush-api-caches", ctx -> Redirects.redirectToOverview.render(flushApiCaches(ctx)));
            jooby.post("/actions/reload-blogs-list", ctx -> Redirects.redirectToOverview.render(reloadBlogsList(ctx)));

            jooby.post("/actions/update-nsfw-filters", ctx -> Redirects.redirectToOverview.render(updateNsfwFilters(ctx)));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object exportAll(Context ctx) {
        String exportType = ctx.lookup("exportType", QUERY, FORM).valueOrNull();

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

    private Object actionsModel(Context ctx) {
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

            List<NodeConfiguration> ndpNodes = new ArrayList<>();
            for (var node : nodeConfigurationService.getAll()) {
                if (node.disabled())
                    continue;
                if (!node.profile().permitDomainDiscovery())
                    continue;

                ndpNodes.add(node);
            }

            return Map.of(
                    "precessionNodes", eligibleNodes,
                    "ndpNodes", ndpNodes);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object discoverNewDomains(Context ctx) throws Exception {
        int node;
        int goal;

        try {
            node = Integer.parseInt(ctx.lookup("node", QUERY, FORM).valueOrNull());
            goal = Integer.parseInt(ctx.lookup("goal", QUERY, FORM).valueOrNull());
        }
        catch (NumberFormatException e) {
            throw new ControlValidationError("Bad parameters", "Node and goal must both be numeric", "/actions");
        }

        if (goal <= 0) {
            throw new ControlValidationError("Bad goal", "The domain count goal must be a positive number", "/actions");
        }

        eventLog.logEvent("USER-ACTION", "DISCOVER-NEW-DOMAINS");

        executorClient.triggerNewDomainsDiscovery(node, goal);

        return "";
    }

    public Object reloadBlogsList(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "RELOAD-BLOGS-LIST");

        domainTypes.reloadDomainsList(DomainTypes.Type.BLOG);

        return "";
    }

    public Object updateNsfwFilters(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "UPDATE-NSFW-FILTERS");

        executorClient.updateNsfwFilters();

        return "";
    }

    public Object flushApiCaches(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "FLUSH-API-CACHES");
        apiOutbox.sendNotice("FLUSH_CACHES", "");

        return "";
    }

    public Object calculateAdjacencies(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "CALCULATE-ADJACENCIES");

        // This is technically not a partitioned operation, but we execute it at node 1
        // and let the effects be global :-)

        executorClient.calculateAdjacencies(1);

        return "";
    }

    public Object reindexAll(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "REINDEX-ALL");

        controlActorService.start(ControlActor.REINDEX_ALL);

        return "";
    }

    public Object reprocessAll(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "REPROCESS-ALL");

        controlActorService.start(ControlActor.REPROCESS_ALL);

        return "";
    }

    public Object recrawlAll(Context ctx) throws Exception {
        eventLog.logEvent("USER-ACTION", "RECRAWL-ALL");

        controlActorService.start(ControlActor.RECRAWL_ALL);

        return "";
    }
}
