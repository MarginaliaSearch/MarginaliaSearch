package nu.marginalia.service.server;

import com.google.inject.Inject;
import io.prometheus.client.exporter.MetricsServlet;
import nu.marginalia.service.module.ServiceConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class MetricsServer {

    private static Logger logger = LoggerFactory.getLogger(MetricsServer.class);

    @Inject
    public MetricsServer(ServiceConfiguration configuration) {
        // If less than zero, we forego setting up a metrics server
        if (configuration.metricsPort() < 0)
            return;

        try {
            Server server = new Server(new InetSocketAddress(configuration.bindAddress(), configuration.metricsPort()));

            ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/");
            server.setHandler(context);

            context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");

            server.start();
        }
        catch (Exception|NoSuchMethodError ex) {
            logger.error("Failed to set up metrics server", ex);
        }
    }
}
