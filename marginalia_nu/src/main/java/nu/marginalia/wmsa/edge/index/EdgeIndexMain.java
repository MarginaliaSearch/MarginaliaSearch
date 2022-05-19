package nu.marginalia.wmsa.edge.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class EdgeIndexMain extends MainClass {
    private EdgeIndexService service;

    @Inject
    public EdgeIndexMain(EdgeIndexService service) throws IOException {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EDGE_INDEX, args);

        Injector injector = Guice.createInjector(
                new EdgeTablesModule(),
                new EdgeIndexModule(),
                new DatabaseModule(),
                new ConfigurationModule()
        );

        injector.getInstance(EdgeIndexMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
