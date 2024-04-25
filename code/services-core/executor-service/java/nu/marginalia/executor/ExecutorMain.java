package nu.marginalia.executor;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.NodeStatusWatcher;

public class ExecutorMain extends MainClass  {
    private final ExecutorSvc service;

    @Inject
    public ExecutorMain(ExecutorSvc service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Executor, args);

        Injector injector = Guice.createInjector(
                new ExecutorModule(),
                new DatabaseModule(false),
                new ServiceDiscoveryModule(),
                new ServiceConfigurationModule(ServiceId.Executor)
        );

        // Ensure that the service registry is initialized early
        injector.getInstance(ServiceRegistryIf.class);

        injector.getInstance(NodeStatusWatcher.class);
        injector.getInstance(ExecutorMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
