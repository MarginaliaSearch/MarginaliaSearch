package nu.marginalia.wmsa.edge.crawler;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class EdgeCrawlerMain extends MainClass {
    private EdgeCrawlerService service;

    @Inject
    public EdgeCrawlerMain(EdgeCrawlerService service) throws IOException {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EDGE_CRAWLER, args);

        Injector injector = Guice.createInjector(
                new EdgeCrawlerModule(),
                new ConfigurationModule(),
                new DatabaseModule()
        );

        injector.getInstance(EdgeCrawlerMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
