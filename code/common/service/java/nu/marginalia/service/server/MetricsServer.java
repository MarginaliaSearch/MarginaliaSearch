package nu.marginalia.service.server;

import com.google.inject.Inject;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

public class MetricsServer {

    private static final Logger logger = LoggerFactory.getLogger(MetricsServer.class);

    @Inject
    public MetricsServer(ServiceConfiguration configuration) {
        // If less than zero, we forego setting up a metrics server
        if (configuration.metricsPort() < 0)
            return;

        try {
            HTTPServer server = HTTPServer.builder()
                    .inetAddress(InetAddress.getByName(configuration.bindAddress()))
                    .port(configuration.metricsPort())
                    .buildAndStart();

            logger.info("MetricsServer listening on {}:{}", configuration.bindAddress(), configuration.metricsPort());
        }
        catch (Exception|NoSuchMethodError ex) {
            logger.error("Failed to set up metrics server", ex);
        }
    }
}
