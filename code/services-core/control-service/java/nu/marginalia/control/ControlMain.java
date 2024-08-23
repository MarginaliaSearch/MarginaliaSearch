package nu.marginalia.control;

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

public class ControlMain extends MainClass {

    @Inject
    public ControlMain(ControlService service) {
    }

    public static void main(String... args) {
        init(ServiceId.Control, args);

        Injector injector = Guice.createInjector(
                new DatabaseModule(true),
                new ControlProcessModule(),
                new ServiceDiscoveryModule(),
                new ServiceConfigurationModule(ServiceId.Control, args));

        // Orchestrate the boot order for the services
        var registry = injector.getInstance(ServiceRegistryIf.class);
        var configuration = injector.getInstance(ServiceConfiguration.class);
        orchestrateBoot(registry, configuration);

        injector.getInstance(ControlMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
