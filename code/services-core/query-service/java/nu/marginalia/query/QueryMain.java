package nu.marginalia.query;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
import io.jooby.Server;
import nu.marginalia.nsfw.NsfwFilterModule;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.service.server.Initialization;

public class QueryMain extends MainClass {
    private final QueryService service;

    @Inject
    public QueryMain(QueryService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Query, args);

        Injector injector = Guice.createInjector(
                new QueryModule(),
                new DatabaseModule(false),
                new NsfwFilterModule(),
                new ServiceDiscoveryModule(),
                new ServiceConfigurationModule(ServiceId.Query)
        );

        // Orchestrate the boot order for the services
        var registry = injector.getInstance(ServiceRegistryIf.class);
        var configuration = injector.getInstance(ServiceConfiguration.class);
        orchestrateBoot(registry, configuration);

        var main = injector.getInstance(QueryMain.class);
        injector.getInstance(Initialization.class).setReady();

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
    }

}
