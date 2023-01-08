package nu.marginalia.wmsa.edge.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

public class EdgeIndexMain extends MainClass {
    private final EdgeIndexService service;

    @Inject
    public EdgeIndexMain(EdgeIndexService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EDGE_INDEX, args);

        Injector injector = Guice.createInjector(
                new EdgeIndexTablesModule(),
                new EdgeIndexModule(),
                new DatabaseModule(),
                new ConfigurationModule()
        );

        injector.getInstance(EdgeIndexMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
