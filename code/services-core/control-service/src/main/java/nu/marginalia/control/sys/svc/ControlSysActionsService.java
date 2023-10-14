package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.client.Context;
import nu.marginalia.control.Redirects;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
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
    private final ExecutorClient executorClient;

    @Inject
    public ControlSysActionsService(MessageQueueFactory mqFactory, DomainTypes domainTypes, ServiceEventLog eventLog, ExecutorClient executorClient) {
        this.apiOutbox = createApiOutbox(mqFactory);
        this.eventLog = eventLog;
        this.domainTypes = domainTypes;
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

    public void register() {
        Spark.post("/public/actions/flush-api-caches", this::flushApiCaches, Redirects.redirectToActors);
        Spark.post("/public/actions/reload-blogs-list", this::reloadBlogsList, Redirects.redirectToActors);
        Spark.post("/public/actions/calculate-adjacencies", this::calculateAdjacencies, Redirects.redirectToActors);
        Spark.post("/public/actions/truncate-links-database", this::truncateLinkDatabase, Redirects.redirectToActors);
        Spark.post("/public/actions/trigger-data-exports", this::triggerDataExports, Redirects.redirectToActors);
    }

    public Object triggerDataExports(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "EXPORT-DATA");

        executorClient.exportData(Context.fromRequest(request));

        return "";
    }

    public Object truncateLinkDatabase(Request request, Response response) throws Exception {

        String footgunLicense = request.queryParams("footgun-license");

        if (!"YES".equals(footgunLicense)) {
            Spark.halt(403);
            return "You must agree to the footgun license to truncate the link database";
        }

        eventLog.logEvent("USER-ACTION", "FLUSH-LINK-DATABASE");

        // FIXME:
//        actors.start(Actor.TRUNCATE_LINK_DATABASE);

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

        // This is technically not a partitioned operation, but we execute it at node zero
        // and let the effects be global :-)

        executorClient.calculateAdjacencies(Context.fromRequest(request), 0);

        return "";
    }
}
