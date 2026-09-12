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
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
import io.jooby.Server;

public class SearchMain extends MainClass {
    private final SearchService service;
    private final Initialization initialization;

    @Inject
    public SearchMain(SearchService service, Initialization initialization) {
        this.service = service;
        this.initialization = initialization;
    }

    public static void main(String... args) {

        init(ServiceId.Search, args);

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

        var main = injector.getInstance(SearchMain.class);

        Jooby.runApp(new String[] { "application.env=prod" }, main.server(), ExecutionMode.WORKER, () -> new Jooby() {
            {
                main.start(this);
            }
        });
    }

    public Server server() {
        return service.createServer();
    }

    public void start(Jooby jooby) {
        service.startJooby(jooby);
        jooby.onStarted(initialization::setReady);
    }
}
