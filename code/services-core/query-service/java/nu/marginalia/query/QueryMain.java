package nu.marginalia.query;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
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

    public void start(Jooby jooby) {
        service.startJooby(jooby);
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
        ServiceRegistryIf registry = injector.getInstance(ServiceRegistryIf.class);
        ServiceConfiguration configuration = injector.getInstance(ServiceConfiguration.class);
        orchestrateBoot(registry, configuration);

        QueryMain main = injector.getInstance(QueryMain.class);
        injector.getInstance(Initialization.class).setReady();

        Jooby.runApp(new String[] { "application.env=prod" }, ExecutionMode.WORKER, () -> new Jooby() {
            { main.start(this); }
        });
    }

}
