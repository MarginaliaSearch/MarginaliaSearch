package nu.marginalia.control;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.client.ServiceMonitors;
import nu.marginalia.control.model.ControlProcess;
import nu.marginalia.control.process.ControlProcesses;
import nu.marginalia.control.svc.*;
import nu.marginalia.db.storage.model.FileStorageId;
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
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ControlService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ServiceMonitors monitors;
    private final MustacheRenderer<Object> indexRenderer;
    private final MustacheRenderer<Map<?,?>> servicesRenderer;
    private final MustacheRenderer<Map<?,?>> processesRenderer;
    private final MustacheRenderer<Map<?,?>> storageRenderer;
    private final StaticResources staticResources;


    @Inject
    public ControlService(BaseServiceParams params,
                          ServiceMonitors monitors,
                          HeartbeatService heartbeatService,
                          EventLogService eventLogService,
                          RendererFactory rendererFactory,
                          ControlProcesses controlProcesses,
                          StaticResources staticResources,
                          MessageQueueViewService messageQueueViewService,
                          ControlFileStorageService controlFileStorageService
                      ) throws IOException {

        super(params);
        this.monitors = monitors;

        indexRenderer = rendererFactory.renderer("control/index");
        servicesRenderer = rendererFactory.renderer("control/services");
        processesRenderer = rendererFactory.renderer("control/processes");
        storageRenderer = rendererFactory.renderer("control/storage");

        this.staticResources = staticResources;

        Spark.get("/public/heartbeats", (req, res) -> {
            res.type("application/json");
            return heartbeatService.getServiceHeartbeats();
        }, gson::toJson);

        Spark.get("/public/", (req, rsp) -> indexRenderer.render(Map.of()));

        Spark.get("/public/services",
                (req, rsp) -> Map.of("services", heartbeatService.getServiceHeartbeats(),
                                     "events", eventLogService.getLastEntries(20)),
                (map) -> servicesRenderer.render((Map<?, ?>) map));

        Spark.get("/public/processes",
                (req, rsp) -> Map.of("processes", heartbeatService.getProcessHeartbeats(),
                              "fsms", controlProcesses.getFsmStates(),
                              "messages", messageQueueViewService.getLastEntries(20)),
                (map) -> processesRenderer.render((Map<?, ?>) map));

        Spark.get("/public/storage",
                (req, rsp) -> Map.of("storage", controlFileStorageService.getStorageList()),
                (map) -> storageRenderer.render((Map<?, ?>) map));

        Spark.post("/public/fsms/:fsm/start", (req, rsp) -> {
            controlProcesses.start(ControlProcess.valueOf(req.params("fsm").toUpperCase()));
            rsp.redirect("/processes");
            return "";
        });
        Spark.post("/public/fsms/:fsm/stop", (req, rsp) -> {
            controlProcesses.stop(ControlProcess.valueOf(req.params("fsm").toUpperCase()));
            rsp.redirect("/processes");
            return "";
        });

        // TODO: This should be a POST
        Spark.get("/public/repartition", (req, rsp) -> {
            controlProcesses.start(ControlProcess.REPARTITION_REINDEX);
            return "OK";
        });

        // TODO: This should be a POST
        Spark.get("/public/reconvert", (req, rsp) -> {
            controlProcesses.start(ControlProcess.RECONVERT_LOAD, FileStorageId.of(11));
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
