package nu.marginalia.wmsa.edge.director;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class EdgeDirectorMain extends MainClass {
    private final EdgeDirectorService service;

    @Inject
    public EdgeDirectorMain(EdgeDirectorService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EDGE_DIRECTOR, args);

        Injector injector = Guice.createInjector(
                new DatabaseModule(),
                new EdgeDirectorModule(),
                new ConfigurationModule()
        );

        injector.getInstance(EdgeDirectorMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
