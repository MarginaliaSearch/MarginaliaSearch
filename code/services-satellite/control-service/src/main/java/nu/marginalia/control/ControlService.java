package nu.marginalia.control;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.client.ServiceMonitors;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.service.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final MqPersistence messageQueuePersistence;


    @Inject
    public ControlService(BaseServiceParams params,
                          ServiceMonitors monitors,
                          HeartbeatService heartbeatService,
                          EventLogService eventLogService,
                          RendererFactory rendererFactory,
                          MqPersistence messageQueuePersistence
                      ) throws IOException {

        super(params);
        this.monitors = monitors;
        indexRenderer = rendererFactory.renderer("control/index");
        servicesRenderer = rendererFactory.renderer("control/services");
        this.messageQueuePersistence = messageQueuePersistence;

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

        Thread reaperThread = new Thread(this::reapMessageQueue, "message-queue-reaper");
        reaperThread.setDaemon(true);
        reaperThread.start();
    }

    private void reapMessageQueue() {

        for (;;) {
            try {
                TimeUnit.MINUTES.sleep(30);

                int outcome = messageQueuePersistence.reapDeadMessages();
                if (outcome > 0) {
                    logger.info("Reaped {} dead messages from message queue", outcome);
                }
            }
            catch (InterruptedException ex) {
                logger.info("Message queue reaper interrupted");
                return;
            }
            catch (Exception ex) {
                logger.error("Message queue reaper failed", ex);
            }
        }
    }

    private void logMonitorStateChange() {
        logger.info("Service state change: {}", monitors.getRunningServices());
    }

}
