package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.actor.ControlActors;
import nu.marginalia.control.actor.Actor;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.search.client.SearchClient;
import nu.marginalia.search.client.SearchMqEndpoints;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.id.ServiceId;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.UUID;

@Singleton
public class ControlActionsService {

    private final ControlActors actors;
    private final SearchClient searchClient;
    private final IndexClient indexClient;
    private final MqOutbox apiOutbox;
    private final ServiceEventLog eventLog;
    private final DomainTypes domainTypes;

    @Inject
    public ControlActionsService(ControlActors actors,
                                 SearchClient searchClient,
                                 IndexClient indexClient,
                                 MessageQueueFactory mqFactory,
                                 ServiceEventLog eventLog,
                                 DomainTypes domainTypes) {

        this.actors = actors;
        this.searchClient = searchClient;
        this.indexClient = indexClient;
        this.apiOutbox = createApiOutbox(mqFactory);
        this.eventLog = eventLog;

        this.domainTypes = domainTypes;
    }

    /** This is a hack to get around the fact that the API service is not a core service
     * and lacks a proper internal API
     */
    private MqOutbox createApiOutbox(MessageQueueFactory mqFactory) {
        String inboxName = ServiceId.Api.name + ":" + "0";
        String outboxName = System.getProperty("service-name", UUID.randomUUID().toString());
        return mqFactory.createOutbox(inboxName, outboxName, UUID.randomUUID());
    }

    public Object calculateAdjacencies(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "CALCULATE-ADJACENCIES");

        actors.start(Actor.ADJACENCY_CALCULATION);

        return "";
    }

    public Object triggerDataExports(Request request, Response response) throws Exception {
        eventLog.logEvent("USER-ACTION", "EXPORT-DATA");
        actors.start(Actor.EXPORT_DATA);

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

    public Object truncateLinkDatabase(Request request, Response response) throws Exception {

        String footgunLicense = request.queryParams("footgun-license");

        if (!"YES".equals(footgunLicense)) {
            Spark.halt(403);
            return "You must agree to the footgun license to truncate the link database";
        }

        eventLog.logEvent("USER-ACTION", "FLUSH-LINK-DATABASE");

        actors.start(Actor.TRUNCATE_LINK_DATABASE);

        return "";
    }

    public Object triggerRepartition(Request request, Response response) throws Exception {
        indexClient.outbox().sendAsync(IndexMqEndpoints.INDEX_REPARTITION, "");

        return null;
    }

    public Object triggerIndexReconstruction(Request request, Response response) throws Exception {
        indexClient.outbox().sendAsync(IndexMqEndpoints.INDEX_REINDEX, "");

        return null;
    }
}
