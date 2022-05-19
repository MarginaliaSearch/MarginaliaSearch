package nu.marginalia.wmsa.edge.search;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;
import spark.Spark;

import java.io.IOException;

public class EdgeSearchMain extends MainClass {
    private final EdgeSearchService service;

    @Inject
    public EdgeSearchMain(EdgeSearchService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EDGE_SEARCH, args);

        Spark.staticFileLocation("/static/edge/");

        Injector injector = Guice.createInjector(
                new EdgeSearchModule(),
                new ConfigurationModule(),
                new DatabaseModule()
        );

        injector.getInstance(EdgeSearchMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
