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
import nu.marginalia.service.server.JoobyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.Extension;
import io.jooby.handler.AssetSource;

import java.util.Map;
import java.util.List;

public class ControlService extends JoobyService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ServiceMonitors monitors;
    private final HeartbeatService heartbeatService;
    private final EventLogService eventLogService;
    private final ControlNodeService controlNodeService;
    private final Extension routes;
    private final MessageQueueService messageQueueService;


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
                          ScheduleService scheduleService,
                          ControlActorService controlActorService,
                          AbortedProcessService abortedProcessService,
                          DomainsManagementService domainsManagementService,
                          WideDomainsService wideDomainsService,
                          ControlErrorHandler errorHandler
                      ) throws Exception {

        super(params, List.of(), List.of());

        this.monitors = monitors;
        this.heartbeatService = heartbeatService;
        this.eventLogService = eventLogService;
        this.controlNodeService = controlNodeService;
        this.messageQueueService = messageQueueService;

        routes = jooby -> {
            // sys
            messageQueueService.register(jooby);
            sysActionsService.register(jooby);
            dataSetsService.register(jooby);
            controlDomainRankingSetsService.register(jooby);
            scheduleService.register(jooby);
            abortedProcessService.register(jooby);

            // node
            controlFileStorageService.register(jooby);
            nodeActionsService.register(jooby);
            controlNodeService.register(jooby);

            // app
            blacklistService.register(jooby);
            searchToBanService.register(jooby);
            apiKeyService.register(jooby);
            domainComplaintService.register(jooby);
            randomExplorationService.register(jooby);
            domainsManagementService.register(jooby);
            wideDomainsService.register(jooby);

            errorHandler.register(jooby);

            var indexRenderer = rendererFactory.renderer("control/index");
            var eventsRenderer = rendererFactory.renderer("control/sys/events");
            var serviceByIdRenderer = rendererFactory.renderer("control/sys/service-by-id");

            jooby.get("/heartbeats", ctx -> {
                ctx.setResponseType("application/json");
                return gson.toJson(heartbeatService.getServiceHeartbeats());
            });

            jooby.get("/", ctx -> indexRenderer.render(overviewModel(ctx)));
            jooby.get("/events", ctx -> eventsRenderer.render(eventLogService.eventsListModel(ctx)));
            jooby.get("/services/{id}", ctx -> serviceByIdRenderer.render(serviceModel(ctx)));

            // Needed to be able to show website screenshots
            jooby.get("/screenshot/{id}", screenshotService::serveScreenshotRequest);
            jooby.assets("/*", AssetSource.create(getClass().getClassLoader(), "/static/control"))
                    .setMaxAge(3600).setETag(true);
        };

        monitors.subscribe(this::logMonitorStateChange);

        controlActorService.startDefaultActors();
    }

    private Object overviewModel(Context ctx) {

        return Map.of("processes", heartbeatService.getProcessHeartbeats(),
                "nodes", controlNodeService.getNodeStatusList(),
                "jobs", heartbeatService.getTaskHeartbeats(),
                "services", heartbeatService.getServiceHeartbeats(),
                "events", eventLogService.getLastEntries(Long.MAX_VALUE, 20)
                );
    }


    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);
        jooby.before(ctx -> ctx.setResponseType("text/html"));
        jooby.after((ctx, result, failure) -> {
            if (!"GET".equals(ctx.getMethod()) && !ctx.header("X-Public").isMissing()) {
                logger.info(MarkerFactory.getMarker("HTTP"), "RSP {}", ctx.getResponseCode().value());
            }
        });
        jooby.install(routes);
    }

    private Object serviceModel(Context ctx) {
        String serviceName = ctx.path("id").value();

        return Map.of(
                "id", serviceName,
                "messages", messageQueueService.getEntriesForInbox(serviceName, Long.MAX_VALUE, 20),
                "events", eventLogService.getLastEntriesForService(serviceName, Long.MAX_VALUE, 20));
    }


    private void logMonitorStateChange() {
        logger.info("Service state change: {}", monitors.getRunningServices());
    }

}
