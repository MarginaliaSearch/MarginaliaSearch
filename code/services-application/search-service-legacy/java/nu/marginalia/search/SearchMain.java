package nu.marginalia.search;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.server.Initialization;
import spark.Spark;

public class SearchMain extends MainClass {
    private final SearchService service;

    @Inject
    public SearchMain(SearchService service) {
        this.service = service;
    }

    public static void main(String... args) {

        init(ServiceId.Search, args);

        Spark.staticFileLocation("/static/search/");

        Injector injector = Guice.createInjector(
                new SearchModule(),
                new ServiceConfigurationModule(ServiceId.Search),
                new ServiceDiscoveryModule(),
                new DatabaseModule(false)
        );
        

        // Orchestrate the boot order for the services
        var registry = injector.getInstance(ServiceRegistryIf.class);
        var configuration = injector.getInstance(ServiceConfiguration.class);
        orchestrateBoot(registry, configuration);

        injector.getInstance(SearchMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
