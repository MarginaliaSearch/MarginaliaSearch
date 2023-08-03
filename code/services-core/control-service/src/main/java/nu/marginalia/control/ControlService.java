package nu.marginalia.control;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.client.ServiceMonitors;
import nu.marginalia.control.model.Actor;
import nu.marginalia.control.model.DomainComplaintModel;
import nu.marginalia.control.model.ProcessHeartbeat;
import nu.marginalia.control.svc.*;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.service.server.*;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ControlService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ServiceMonitors monitors;
    private final HeartbeatService heartbeatService;
    private final EventLogService eventLogService;
    private final ApiKeyService apiKeyService;
    private final DomainComplaintService domainComplaintService;
    private final ControlActorService controlActorService;
    private final StaticResources staticResources;
    private final MessageQueueViewService messageQueueViewService;
    private final ControlFileStorageService controlFileStorageService;


    @Inject
    public ControlService(BaseServiceParams params,
                          ServiceMonitors monitors,
                          HeartbeatService heartbeatService,
                          EventLogService eventLogService,
                          RendererFactory rendererFactory,
                          ControlActorService controlActorService,
                          StaticResources staticResources,
                          MessageQueueViewService messageQueueViewService,
                          ControlFileStorageService controlFileStorageService,
                          ApiKeyService apiKeyService,
                          DomainComplaintService domainComplaintService,
                          MqPersistence persistence
                      ) throws IOException {

        super(params);
        this.monitors = monitors;
        this.heartbeatService = heartbeatService;
        this.eventLogService = eventLogService;
        this.apiKeyService = apiKeyService;
        this.domainComplaintService = domainComplaintService;

        var indexRenderer = rendererFactory.renderer("control/index");
        var servicesRenderer = rendererFactory.renderer("control/services");
        var serviceByIdRenderer = rendererFactory.renderer("control/service-by-id");
        var actorsRenderer = rendererFactory.renderer("control/actors");
        var actorDetailsRenderer = rendererFactory.renderer("control/actor-details");
        var storageRenderer = rendererFactory.renderer("control/storage-overview");
        var storageSpecsRenderer = rendererFactory.renderer("control/storage-specs");
        var storageCrawlsRenderer = rendererFactory.renderer("control/storage-crawls");
        var storageProcessedRenderer = rendererFactory.renderer("control/storage-processed");

        var apiKeysRenderer = rendererFactory.renderer("control/api-keys");
        var domainComplaintsRenderer = rendererFactory.renderer("control/domain-complaints");

        var storageDetailsRenderer = rendererFactory.renderer("control/storage-details");
        var updateMessageStateRenderer = rendererFactory.renderer("control/dialog-update-message-state");

        this.controlActorService = controlActorService;

        this.staticResources = staticResources;
        this.messageQueueViewService = messageQueueViewService;
        this.controlFileStorageService = controlFileStorageService;

        Spark.get("/public/heartbeats", (req, res) -> {
            res.type("application/json");
            return heartbeatService.getServiceHeartbeats();
        }, gson::toJson);

        Spark.get("/public/", (req, rsp) -> indexRenderer.render(Map.of()));

        Spark.get("/public/services", this::servicesModel, servicesRenderer::render);
        Spark.get("/public/services/:id", this::serviceModel, serviceByIdRenderer::render);
        Spark.get("/public/messages/:id", this::messageModel, gson::toJson);
        Spark.get("/public/actors", this::processesModel, actorsRenderer::render);
        Spark.get("/public/actors/:fsm", this::actorDetailsModel, actorDetailsRenderer::render);
        Spark.get("/public/storage", this::storageModel, storageRenderer::render);
        Spark.get("/public/storage/specs", this::storageModelSpecs, storageSpecsRenderer::render);
        Spark.get("/public/storage/crawls", this::storageModelCrawls, storageCrawlsRenderer::render);
        Spark.get("/public/storage/processed", this::storageModelProcessed, storageProcessedRenderer::render);
        Spark.get("/public/storage/:id", this::storageDetailsModel, storageDetailsRenderer::render);
        Spark.get("/public/storage/:id/file", controlFileStorageService::downloadFileFromStorage);


        final HtmlRedirect redirectToServices = new HtmlRedirect("/services");
        final HtmlRedirect redirectToProcesses = new HtmlRedirect("/actors");
        final HtmlRedirect redirectToApiKeys = new HtmlRedirect("/api-keys");
        final HtmlRedirect redirectToStorage = new HtmlRedirect("/storage");
        final HtmlRedirect redirectToComplaints = new HtmlRedirect("/complaints");

        Spark.post("/public/fsms/:fsm/start", controlActorService::startFsm, redirectToProcesses);
        Spark.post("/public/fsms/:fsm/stop", controlActorService::stopFsm, redirectToProcesses);
        Spark.post("/public/storage/:fid/crawl", controlActorService::triggerCrawling, redirectToProcesses);
        Spark.post("/public/storage/:fid/recrawl", controlActorService::triggerRecrawling, redirectToProcesses);
        Spark.post("/public/storage/:fid/process", controlActorService::triggerProcessing, redirectToProcesses);
        Spark.post("/public/storage/:fid/load", controlActorService::loadProcessedData, redirectToProcesses);

        Spark.post("/public/storage/specs", controlActorService::createCrawlSpecification, redirectToStorage);
        Spark.post("/public/storage/:fid/delete", controlFileStorageService::flagFileForDeletionRequest, redirectToStorage);

        Spark.get("/public/api-keys", this::apiKeysModel, apiKeysRenderer::render);
        Spark.post("/public/api-keys", this::createApiKey, redirectToApiKeys);
        Spark.delete("/public/api-keys/:key", this::deleteApiKey, redirectToApiKeys);
        // HTML forms don't support the DELETE verb :-(
        Spark.post("/public/api-keys/:key/delete", this::deleteApiKey, redirectToApiKeys);

        Spark.get("/public/complaints", this::complaintsModel, domainComplaintsRenderer::render);
        Spark.post("/public/complaints/:domain", this::reviewComplaint, redirectToComplaints);

        Spark.get("/public/message/:id/state", (rq, rsp) -> persistence.getMessage(Long.parseLong(rq.params("id"))), updateMessageStateRenderer::render);
        Spark.post("/public/message/:id/state", (rq, rsp) -> {
            MqMessageState state = MqMessageState.valueOf(rq.queryParams("state"));
            long id = Long.parseLong(rq.params("id"));
            persistence.updateMessageState(id, state);
            return "";
        }, redirectToProcesses);

        Spark.get("/public/:resource", this::serveStatic);

        monitors.subscribe(this::logMonitorStateChange);
    }

    private Object complaintsModel(Request request, Response response) {
        Map<Boolean, List<DomainComplaintModel>> complaintsByReviewed =
                domainComplaintService.getComplaints().stream().collect(Collectors.partitioningBy(DomainComplaintModel::reviewed));

        var reviewed = complaintsByReviewed.get(true);
        var unreviewed = complaintsByReviewed.get(false);

        reviewed.sort(Comparator.comparing(DomainComplaintModel::reviewDate).reversed());
        unreviewed.sort(Comparator.comparing(DomainComplaintModel::fileDate).reversed());

        return Map.of("complaintsNew", unreviewed, "complaintsReviewed", reviewed);
    }

    private Object reviewComplaint(Request request, Response response) {
        var domain = new EdgeDomain(request.params("domain"));
        String action = request.queryParams("action");

        logger.info("Reviewing complaint for domain {} with action {}", domain, action);

        switch (action) {
            case "noop" -> domainComplaintService.reviewNoAction(domain);
            case "appeal" -> domainComplaintService.approveAppealBlacklisting(domain);
            case "blacklist" -> domainComplaintService.blacklistDomain(domain);
            default -> throw new UnsupportedOperationException();
        }

        return "";
    }

    private Object createApiKey(Request request, Response response) {
        String license = request.queryParams("license");
        String name = request.queryParams("name");
        String email = request.queryParams("email");
        int rate = Integer.parseInt(request.queryParams("rate"));

        if (StringUtil.isBlank(license) ||
            StringUtil.isBlank(name) ||
            StringUtil.isBlank(email) ||
            rate <= 0)
        {
            response.status(400);
            return "";
        }

        apiKeyService.addApiKey(license, name, email, rate);

        return "";
    }

    private Object deleteApiKey(Request request, Response response) {
        String licenseKey = request.params("key");
        apiKeyService.deleteApiKey(licenseKey);
        return "";
    }

    private Object apiKeysModel(Request request, Response response) {
        return Map.of("apikeys", apiKeyService.getApiKeys());
    }


    @Override
    public void logRequest(Request request) {
        if ("GET".equals(request.requestMethod()))
            return;

        super.logRequest(request);
    }

    @Override
    public void logResponse(Request request, Response response) {
        if ("GET".equals(request.requestMethod()))
            return;

        super.logResponse(request, response);
    }


    private Object messageModel(Request request, Response response) {
        var message = messageQueueViewService.getMessage(Long.parseLong(request.params("id")));
        if (message != null) {
            response.type("application/json");
            return message;
        }
        else {
            response.status(404);
            return "";
        }
    }

    private Object serviceModel(Request request, Response response) {
        String serviceName = request.params("id");

        return Map.of(
                "id", serviceName,
                "events", eventLogService.getLastEntriesForService(serviceName, 20));
    }

    private Object storageModel(Request request, Response response) {
        return Map.of("storage", controlFileStorageService.getStorageList());
    }

    private Object storageDetailsModel(Request request, Response response) throws SQLException {
        return Map.of("storage", controlFileStorageService.getFileStorageWithRelatedEntries(FileStorageId.parse(request.params("id"))));
    }
    private Object storageModelSpecs(Request request, Response response) {
        return Map.of("storage", controlFileStorageService.getStorageList(FileStorageType.CRAWL_SPEC));
    }
    private Object storageModelCrawls(Request request, Response response) {
        return Map.of("storage", controlFileStorageService.getStorageList(FileStorageType.CRAWL_DATA));
    }
    private Object storageModelProcessed(Request request, Response response) {
        return Map.of("storage", controlFileStorageService.getStorageList(FileStorageType.PROCESSED_DATA));
    }
    private Object servicesModel(Request request, Response response) {
        return Map.of("services", heartbeatService.getServiceHeartbeats(),
                      "events", eventLogService.getLastEntries(20));
    }

    private Object processesModel(Request request, Response response) {
        var heartbeatsAll = heartbeatService.getProcessHeartbeats();
        var byIsJob = heartbeatsAll.stream().collect(Collectors.partitioningBy(ProcessHeartbeat::isServiceJob));

        return Map.of("processes", byIsJob.get(false),
                      "jobs", byIsJob.get(true),
                      "actors", controlActorService.getActorStates(),
                      "messages", messageQueueViewService.getLastEntries(20));
    }
    private Object actorDetailsModel(Request request, Response response) {
        final Actor actor = Actor.valueOf(request.params("fsm").toUpperCase());
        final String inbox = actor.id();

        return Map.of(
                "actor", actor,
                "state-graph", controlActorService.getActorStateGraph(actor),
                "messages", messageQueueViewService.getLastEntriesForInbox(inbox, 20));
    }
    private Object serveStatic(Request request, Response response) {
        String resource = request.params("resource");

        staticResources.serveStatic("control", resource, request, response);

        return "";
    }


    private void logMonitorStateChange() {
        logger.info("Service state change: {}", monitors.getRunningServices());
    }

}
