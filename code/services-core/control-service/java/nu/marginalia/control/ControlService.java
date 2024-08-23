package nu.marginalia.control;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.control.actor.ControlActorService;
import nu.marginalia.control.app.svc.*;
import nu.marginalia.control.node.svc.ControlFileStorageService;
import nu.marginalia.control.node.svc.ControlNodeActionsService;
import nu.marginalia.control.node.svc.ControlNodeService;
import nu.marginalia.control.sys.svc.*;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.service.ServiceMonitors;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.SparkService;
import nu.marginalia.service.server.StaticResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.util.Map;

public class ControlService extends SparkService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ServiceMonitors monitors;
    private final HeartbeatService heartbeatService;
    private final EventLogService eventLogService;
    private final ControlNodeService controlNodeService;
    private final StaticResources staticResources;
    private final MessageQueueService messageQueueService;


    @Inject
    public ControlService(BaseServiceParams params,
                          ServiceMonitors monitors,
                          HeartbeatService heartbeatService,
                          EventLogService eventLogService,
                          ControlRendererFactory rendererFactory,
                          StaticResources staticResources,
                          MessageQueueService messageQueueService,
                          ControlFileStorageService controlFileStorageService,
                          ApiKeyService apiKeyService,
                          DomainComplaintService domainComplaintService,
                          ControlBlacklistService blacklistService,
                          ControlNodeActionsService nodeActionsService,
                          ControlSysActionsService sysActionsService,
                          ScreenshotService screenshotService,
                          SearchToBanService searchToBanService,
                          RandomExplorationService randomExplorationService,
                          DataSetsService dataSetsService,
                          ControlNodeService controlNodeService,
                          ControlDomainRankingSetsService controlDomainRankingSetsService,
                          ControlActorService controlActorService,
                          AbortedProcessService abortedProcessService,
                          ControlErrorHandler errorHandler
                      ) throws IOException {

        super(params);

        this.monitors = monitors;
        this.heartbeatService = heartbeatService;
        this.eventLogService = eventLogService;
        this.controlNodeService = controlNodeService;

        // sys
        messageQueueService.register();
        sysActionsService.register();
        dataSetsService.register();
        controlDomainRankingSetsService.register();
        abortedProcessService.register();

        // node
        controlFileStorageService.register();
        nodeActionsService.register();
        controlNodeService.register();

        // app
        blacklistService.register();
        searchToBanService.register();
        apiKeyService.register();
        domainComplaintService.register();
        randomExplorationService.register();

        errorHandler.register();

        var indexRenderer = rendererFactory.renderer("control/index");
        var eventsRenderer = rendererFactory.renderer("control/sys/events");
        var serviceByIdRenderer = rendererFactory.renderer("control/sys/service-by-id");

        var actionsViewRenderer = rendererFactory.renderer("control/actions");

        this.staticResources = staticResources;
        this.messageQueueService = messageQueueService;

        Spark.get("/heartbeats", (req, res) -> {
            res.type("application/json");
            return heartbeatService.getServiceHeartbeats();
        }, gson::toJson);

        Spark.get("/", this::overviewModel, indexRenderer::render);

        Spark.get("/actions", (req,rs) -> new Object() , actionsViewRenderer::render);
        Spark.get("/events", eventLogService::eventsListModel , eventsRenderer::render);
        Spark.get("/services/:id", this::serviceModel, serviceByIdRenderer::render);

        // Needed to be able to show website screenshots
        Spark.get("/screenshot/:id", screenshotService::serveScreenshotRequest);

        Spark.get("/:resource", this::serveStatic);


        monitors.subscribe(this::logMonitorStateChange);

        controlActorService.startDefaultActors();
    }

    private Object overviewModel(Request request, Response response) {

        return Map.of("processes", heartbeatService.getProcessHeartbeats(),
                "nodes", controlNodeService.getNodeStatusList(),
                "jobs", heartbeatService.getTaskHeartbeats(),
                "services", heartbeatService.getServiceHeartbeats(),
                "events", eventLogService.getLastEntries(Long.MAX_VALUE, 20)
                );
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

    private Object serviceModel(Request request, Response response) {
        String serviceName = request.params("id");

        return Map.of(
                "id", serviceName,
                "messages", messageQueueService.getEntriesForInbox(serviceName, Long.MAX_VALUE, 20),
                "events", eventLogService.getLastEntriesForService(serviceName, Long.MAX_VALUE, 20));
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
