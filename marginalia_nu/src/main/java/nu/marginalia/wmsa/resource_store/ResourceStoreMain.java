package nu.marginalia.wmsa.resource_store;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.server.Initialization;

import java.io.IOException;

public class ResourceStoreMain extends MainClass {
    private final ResourceStoreService service;

    @Inject
    public ResourceStoreMain(ResourceStoreService service) {
        this.service = service;

    }

    public static void main(String... args) {
        init(ServiceDescriptor.RESOURCE_STORE, args);

        Injector injector = Guice.createInjector(
                new ResourceStoreModule(),
                new ConfigurationModule()
        );
        injector.getInstance(ResourceStoreMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
