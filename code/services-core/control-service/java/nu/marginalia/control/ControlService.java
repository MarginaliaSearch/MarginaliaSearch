package nu.marginalia.control;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
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
import nu.marginalia.service.server.JoobyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;
import java.util.Map;

public class ControlService extends JoobyService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Marker httpMarker = MarkerFactory.getMarker("HTTP");
    private final Gson gson = GsonFactory.get();

    private final ServiceMonitors monitors;
    private final HeartbeatService heartbeatService;
    private final EventLogService eventLogService;
    private final ControlNodeService controlNodeService;
    private final MessageQueueService messageQueueService;

    private final ControlFileStorageService controlFileStorageService;
    private final ApiKeyService apiKeyService;
    private final DomainComplaintService domainComplaintService;
    private final ControlBlacklistService blacklistService;
    private final ControlNodeActionsService nodeActionsService;
    private final ControlSysActionsService sysActionsService;
    private final ScreenshotService screenshotService;
    private final SearchToBanService searchToBanService;
    private final RandomExplorationService randomExplorationService;
    private final DataSetsService dataSetsService;
    private final ControlDomainRankingSetsService controlDomainRankingSetsService;
    private final AbortedProcessService abortedProcessService;
    private final DomainsManagementService domainsManagementService;
    private final ControlErrorHandler errorHandler;
    private final ControlRendererFactory rendererFactory;

    private ControlRendererFactory.Renderer indexRenderer;
    private ControlRendererFactory.Renderer eventsRenderer;
    private ControlRendererFactory.Renderer serviceByIdRenderer;

    @Inject
    public ControlService(BaseServiceParams params,
                          ServiceMonitors monitors,
                          HeartbeatService heartbeatService,
                          EventLogService eventLogService,
                          ControlRendererFactory rendererFactory,
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
                          DomainsManagementService domainsManagementService,
                          ControlErrorHandler errorHandler
                      ) throws Exception {

        super(params, List.of(), List.of());

        this.monitors = monitors;
        this.heartbeatService = heartbeatService;
        this.eventLogService = eventLogService;
        this.controlNodeService = controlNodeService;
        this.messageQueueService = messageQueueService;
        this.controlFileStorageService = controlFileStorageService;
        this.apiKeyService = apiKeyService;
        this.domainComplaintService = domainComplaintService;
        this.blacklistService = blacklistService;
        this.nodeActionsService = nodeActionsService;
        this.sysActionsService = sysActionsService;
        this.screenshotService = screenshotService;
        this.searchToBanService = searchToBanService;
        this.randomExplorationService = randomExplorationService;
        this.dataSetsService = dataSetsService;
        this.controlDomainRankingSetsService = controlDomainRankingSetsService;
        this.abortedProcessService = abortedProcessService;
        this.domainsManagementService = domainsManagementService;
        this.errorHandler = errorHandler;
        this.rendererFactory = rendererFactory;

        monitors.subscribe(this::logMonitorStateChange);
        controlActorService.startDefaultActors();
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        try {
            messageQueueService.register(jooby);
            sysActionsService.register(jooby);
            dataSetsService.register(jooby);
            controlDomainRankingSetsService.register(jooby);
            abortedProcessService.register(jooby);

            controlFileStorageService.register(jooby);
            nodeActionsService.register(jooby);
            controlNodeService.register(jooby);

            blacklistService.register(jooby);
            searchToBanService.register(jooby);
            apiKeyService.register(jooby);
            domainComplaintService.register(jooby);
            randomExplorationService.register(jooby);
            domainsManagementService.register(jooby);

            errorHandler.register(jooby);

            indexRenderer = rendererFactory.renderer("control/index");
            eventsRenderer = rendererFactory.renderer("control/sys/events");
            serviceByIdRenderer = rendererFactory.renderer("control/sys/service-by-id");

            jooby.get("/heartbeats", this::serveHeartbeats);
            jooby.get("/", this::overviewModel);
            jooby.get("/events", this::serveEvents);
            jooby.get("/services/{id}", this::serviceModel);
            jooby.get("/screenshot/{id}", screenshotService::serveScreenshotRequest);

            jooby.before(this::logRequest);
            jooby.after(this::logResponse);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void logRequest(Context ctx) {
        if ("GET".equals(ctx.getMethod())) return;

        String url = ctx.getRequestPath();
        String qs = ctx.queryString();
        if (qs != null && !qs.isEmpty()) {
            url = url + "?" + qs;
        }
        logger.info(httpMarker, "PUBLIC: {} {}", ctx.getMethod(), url);
    }

    private void logResponse(Context ctx, Object result, Throwable failure) {
        if ("GET".equals(ctx.getMethod())) return;

        logger.info(httpMarker, "RSP {}", ctx.getResponseCode());
    }

    private Object serveHeartbeats(Context ctx) {
        ctx.setResponseType(MediaType.json);
        return gson.toJson(heartbeatService.getServiceHeartbeats());
    }

    private Object overviewModel(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return indexRenderer.render(Map.of(
                "processes", heartbeatService.getProcessHeartbeats(),
                "nodes", controlNodeService.getNodeStatusList(),
                "jobs", heartbeatService.getTaskHeartbeats(),
                "services", heartbeatService.getServiceHeartbeats(),
                "events", eventLogService.getLastEntries(Long.MAX_VALUE, 20)
        ));
    }

    private Object serveEvents(Context ctx) {
        ctx.setResponseType(MediaType.html);
        return eventsRenderer.render(eventLogService.eventsListModel(ctx));
    }

    private Object serviceModel(Context ctx) {
        String serviceName = ctx.path("id").value();
        ctx.setResponseType(MediaType.html);
        return serviceByIdRenderer.render(Map.of(
                "id", serviceName,
                "messages", messageQueueService.getEntriesForInbox(serviceName, Long.MAX_VALUE, 20),
                "events", eventLogService.getLastEntriesForService(serviceName, Long.MAX_VALUE, 20)));
    }

    private void logMonitorStateChange() {
        logger.info("Service state change: {}", monitors.getRunningServices());
    }
}
