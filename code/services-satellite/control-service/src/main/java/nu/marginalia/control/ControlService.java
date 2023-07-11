package nu.marginalia.control;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.client.ServiceMonitors;
import nu.marginalia.control.model.ControlProcess;
import nu.marginalia.control.process.ControlProcesses;
import nu.marginalia.control.svc.EventLogService;
import nu.marginalia.control.svc.HeartbeatService;
import nu.marginalia.control.svc.MessageQueueMonitorService;
import nu.marginalia.control.svc.MessageQueueViewService;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ControlService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ServiceMonitors monitors;
    private final MustacheRenderer<Object> indexRenderer;
    private final MustacheRenderer<Map<?,?>> servicesRenderer;
    private final MustacheRenderer<Map<?,?>> processesRenderer;
    private final MustacheRenderer<Map<?,?>> eventsRenderer;
    private final MustacheRenderer<Map<?,?>> messageQueueRenderer;
    private final MqPersistence messageQueuePersistence;
    private final StaticResources staticResources;
    private final MessageQueueMonitorService messageQueueMonitorService;


    @Inject
    public ControlService(BaseServiceParams params,
                          ServiceMonitors monitors,
                          HeartbeatService heartbeatService,
                          EventLogService eventLogService,
                          RendererFactory rendererFactory,
                          MqPersistence messageQueuePersistence,
                          ControlProcesses controlProcesses,
                          StaticResources staticResources,
                          MessageQueueViewService messageQueueViewService,
                          MessageQueueMonitorService messageQueueMonitorService
                      ) throws IOException {

        super(params);
        this.monitors = monitors;

        indexRenderer = rendererFactory.renderer("control/index");
        servicesRenderer = rendererFactory.renderer("control/services");
        processesRenderer = rendererFactory.renderer("control/processes");
        eventsRenderer = rendererFactory.renderer("control/events");
        messageQueueRenderer = rendererFactory.renderer("control/message-queue");

        this.messageQueuePersistence = messageQueuePersistence;
        this.staticResources = staticResources;
        this.messageQueueMonitorService = messageQueueMonitorService;

        Spark.get("/public/heartbeats", (req, res) -> {
            res.type("application/json");
            return heartbeatService.getServiceHeartbeats();
        }, gson::toJson);

        Spark.get("/public/", (req, rsp) -> indexRenderer.render(Map.of()));

        Spark.get("/public/services", (req, rsp) -> servicesRenderer.render(Map.of("heartbeats", heartbeatService.getServiceHeartbeats())));
        Spark.get("/public/processes", (req, rsp) -> processesRenderer.render(Map.of("heartbeats", heartbeatService.getProcessHeartbeats())));
        Spark.get("/public/events", (req, rsp) -> eventsRenderer.render(Map.of("events", eventLogService.getLastEntries(20))));
        Spark.get("/public/message-queue", (req, rsp) -> messageQueueRenderer.render(Map.of("messages", messageQueueViewService.getLastEntries(20))));

        // TODO: This should be a POST
        Spark.get("/public/repartition", (req, rsp) -> {
            controlProcesses.start(ControlProcess.REPARTITION_REINDEX);
            return "OK";
        });

        Spark.get("/public/:resource", this::serveStatic);

        monitors.subscribe(this::logMonitorStateChange);
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
