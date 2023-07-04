package nu.marginalia.control;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.client.ServiceMonitors;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.service.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.IOException;
import java.util.Map;

public class ControlService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ServiceMonitors monitors;
    private final MustacheRenderer<Object> indexRenderer;
    private final MustacheRenderer<Map<?,?>> servicesRenderer;


    @Inject
    public ControlService(BaseServiceParams params,
                          ServiceMonitors monitors,
                          HeartbeatService heartbeatService,
                          EventLogService eventLogService,
                          RendererFactory rendererFactory
                      ) throws IOException {

        super(params);
        this.monitors = monitors;
        indexRenderer = rendererFactory.renderer("control/index");
        servicesRenderer = rendererFactory.renderer("control/services");

        Spark.get("/public/heartbeats", (req, res) -> {
            res.type("application/json");
            return heartbeatService.getHeartbeats();
        }, gson::toJson);

        Spark.get("/public/", (req, rsp) -> indexRenderer.render(Map.of()));
        Spark.get("/public/services", (req, rsp) -> servicesRenderer.render(
                Map.of("heartbeats", heartbeatService.getHeartbeats(),
                        "events", eventLogService.getLastEntries(100)
                        )));

        monitors.subscribe(this::logMonitorStateChange);


    }

    private void logMonitorStateChange() {
        logger.info("Service state change: {}", monitors.getRunningServices());
    }

}
