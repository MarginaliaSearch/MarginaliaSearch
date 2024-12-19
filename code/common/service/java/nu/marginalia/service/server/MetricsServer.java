package nu.marginalia.service.server;

import com.google.inject.Inject;
import io.prometheus.client.exporter.MetricsServlet;
import nu.marginalia.service.module.ServiceConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.net.InetSocketAddress;

public class MetricsServer {

    @Inject
    public MetricsServer(ServiceConfiguration configuration) throws Exception {
        // If less than zero, we forego setting up a metrics server
        if (configuration.metricsPort() < 0)
            return;

        Server server = new Server(new InetSocketAddress(configuration.bindAddress(), configuration.metricsPort()));

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");

        server.start();
    }
}
