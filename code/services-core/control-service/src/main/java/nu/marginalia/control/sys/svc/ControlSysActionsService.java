package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.client.Context;
import nu.marginalia.control.Redirects;
import nu.marginalia.control.actor.ControlActor;
import nu.marginalia.control.actor.ControlActorService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.id.ServiceId;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.UUID;

public class ControlSysActionsService {
    private final MqOutbox apiOutbox;
    private final DomainTypes domainTypes;
    private final ServiceEventLog eventLog;
    private final RendererFactory rendererFactory;
    private final ControlActorService controlActorService;
    private final ExecutorClient executorClient;

    @Inject
    public ControlSysActionsService(MessageQueueFactory mqFactory,
                                    DomainTypes domainTypes,
                                    ServiceEventLog eventLog,
                                    RendererFactory rendererFactory,
                                    ControlActorService controlActorService,
                                    ExecutorClient executorClient)
    {
        this.apiOutbox = createApiOutbox(mqFactory);
        this.eventLog = eventLog;
        this.domainTypes = domainTypes;
        this.rendererFactory = rendererFactory;
        this.controlActorService = controlActorService;
        this.executorClient = executorClient;
    }

    /** This is a hack to get around the fact that the API service is not a core service
     * and lacks a proper internal API
     */
    private MqOutbox createApiOutbox(MessageQueueFactory mqFactory) {
        String inboxName = ServiceId.Api.name + ":" + "0";
        String outboxName = System.getProperty("service-name", UUID.randomUUID().toString());
        return mqFactory.createOutbox(inboxName, 0, outboxName, 0, UUID.randomUUID());
    }

    @SneakyThrows
    public void register() {
        var actionsView = rendererFactory.renderer("control/sys/sys-actions");

        Spark.get("/public/actions", (rq,rsp) -> new Object(), actionsView::render);
        Spark.post("/public/actions/recalculate-adjacencies-graph", this::calculateAdjacencies, Redirects.redirectToOverview);
        Spark.post("/public/actions/reindex-all", this::reindexAll, Redirects.redirectToOverview);
        Spark.post("/public/actions/reprocess-all", this::reprocessAll, Redirects.redirectToOverview);
        Spark.post("/public/actions/flush-api-caches", this::flushApiCaches, Redirects.redirectToOverview);
        Spark.post("/public/actions/reload-blogs-list", this::reloadBlogsList, Redirects.redirectToOverview);
        Spark.post("/public/actions/trigger-data-exports", this::triggerDataExports, Redirects.redirectToOverview);
    }

    public Object triggerDataExports(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "EXPORT-DATA");

        executorClient.exportData(Context.fromRequest(request));

        return "";
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

        executorClient.calculateAdjacencies(Context.fromRequest(request), 1);

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
}
