package nu.marginalia.assistant;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.server.Initialization;

public class AssistantMain extends MainClass {
    private final AssistantService service;

    @Inject
    public AssistantMain(AssistantService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Assistant, args);

        Injector injector = Guice.createInjector(
                new AssistantModule(),
                new ServiceConfigurationModule(ServiceId.Assistant),
                new ServiceDiscoveryModule(),
                new DatabaseModule(false)
        );


        // Ensure that the service registry is initialized early
        injector.getInstance(ServiceRegistryIf.class);

        injector.getInstance(AssistantMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
