package nu.marginalia.wmsa.edge.explorer;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;
import spark.Spark;

public class ExplorerMain extends MainClass {
    final ExplorerService service;

    @Inject
    public ExplorerMain(ExplorerService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.EXPLORER, args);

        Spark.staticFileLocation("/static/explore/");

        Injector injector = Guice.createInjector(
                new ConfigurationModule(),
                new DatabaseModule()
        );

        injector.getInstance(ExplorerMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
