package nu.marginalia.wmsa.edge.archive;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

public class EdgeArchiveMain extends MainClass {
    private final EdgeArchiveService service;

    @Inject
    public EdgeArchiveMain(EdgeArchiveService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EDGE_ARCHIVE, args);

        Injector injector = Guice.createInjector(
                new EdgeArchiveModule(),
                new ConfigurationModule(),
                new DatabaseModule()
        );

        injector.getInstance(EdgeArchiveMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
