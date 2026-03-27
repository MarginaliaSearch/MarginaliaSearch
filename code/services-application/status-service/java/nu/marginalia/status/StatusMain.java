package nu.marginalia.status;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.service.server.Initialization;

public class StatusMain extends MainClass {
    private final StatusService service;

    @Inject
    public StatusMain(StatusService service) {
        this.service = service;
    }

    public void start(Jooby jooby) {
        service.startJooby(jooby);
    }

    public static void main(String... args) {

        init(ServiceId.Status, args);

        Injector injector = Guice.createInjector(
                new ServiceConfigurationModule(ServiceId.Status),
                new ServiceDiscoveryModule(),
                new StatusModule(),
                new DatabaseModule(false)
        );


        // Orchestrate the boot order for the services
        ServiceRegistryIf registry = injector.getInstance(ServiceRegistryIf.class);
        ServiceConfiguration configuration = injector.getInstance(ServiceConfiguration.class);
        orchestrateBoot(registry, configuration);

        StatusMain main = injector.getInstance(StatusMain.class);
        injector.getInstance(Initialization.class).setReady();

        Jooby.runApp(new String[] { "application.env=prod" }, ExecutionMode.WORKER, () -> new Jooby() {
            { main.start(this); }
        });
    }
}
