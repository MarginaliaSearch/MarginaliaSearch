package nu.marginalia.wmsa.configuration.server;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.prometheus.client.exporter.MetricsServlet;
import lombok.SneakyThrows;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class MetricsServer {

    @SneakyThrows
    @Inject
    public MetricsServer(@Named("metrics-server-port") int port) {
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");

        server.start();
    }
}
