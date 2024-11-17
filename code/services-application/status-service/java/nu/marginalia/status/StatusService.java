package nu.marginalia.status;

import com.google.inject.Inject;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import nu.marginalia.status.db.StatusMetricDb;
import nu.marginalia.status.endpoints.ApiEndpoint;
import nu.marginalia.status.endpoints.MainSearchEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static spark.Spark.get;

public class StatusService extends Service {
    private final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(5);

    private final StatusMetricDb statusMetricDb;
    private final List<StatusMetric> metrics = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(StatusService.class);

    @Inject
    public StatusService(BaseServiceParams params,
                         StatusMetricDb statusMetricDb,
                         ApiEndpoint apiEndpoint,
                         RendererFactory rendererFactory,
                         MainSearchEndpoint mainSearchEndpoint
                         ) throws Exception {
        super(params);

        this.statusMetricDb = statusMetricDb;

        metrics.add(new StatusMetric("Public JSON Api", apiEndpoint::check));
        metrics.add(new StatusMetric("Public HTML Endpoint", mainSearchEndpoint::check));

        scheduledExecutorService.scheduleAtFixedRate(this::poll, 0, 15, TimeUnit.SECONDS);
        scheduledExecutorService.scheduleAtFixedRate(statusMetricDb::pruneOldResults, 0, 1, TimeUnit.HOURS);

        var statusRenderer = rendererFactory.renderer("status");

        get("/", (req, res) -> statusRenderer.render(
                Map.of("measurements", statusMetricDb.getAllStatistics()))
        );
    }

    private void poll() {
        for (StatusMetric metric : metrics) {
            try {
                statusMetricDb.saveResult(metric.update());
            }
            catch (Exception e) {
                logger.error("Failed to update metric " + metric.getName(), e);
            }
        }
    }
}
