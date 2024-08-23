package nu.marginalia.explorer;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.service.server.Initialization;
import spark.Spark;

public class ExplorerMain extends MainClass {
    final ExplorerService service;

    @Inject
    public ExplorerMain(ExplorerService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Explorer, args);

        Spark.staticFileLocation("/static/explore/");

        Injector injector = Guice.createInjector(
                new ServiceConfigurationModule(ServiceId.Explorer, args),
                new ServiceDiscoveryModule(),
                new ExplorerModule(),
                new DatabaseModule(false)
        );

        // Orchestrate the boot order for the services
        var registry = injector.getInstance(ServiceRegistryIf.class);
        var configuration = injector.getInstance(ServiceConfiguration.class);
        orchestrateBoot(registry, configuration);

        injector.getInstance(ExplorerMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
