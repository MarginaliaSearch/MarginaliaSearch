package nu.marginalia.wmsa.resource_store;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ConfigurationModule;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.wmsa.renderer.WmsaServiceDescriptors;

public class ResourceStoreMain extends MainClass {
    private final ResourceStoreService service;

    @Inject
    public ResourceStoreMain(ResourceStoreService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Other_ResourceStore, args);

        Injector injector = Guice.createInjector(
                new ResourceStoreModule(),
                new ConfigurationModule(WmsaServiceDescriptors.descriptors, ServiceId.Other_ResourceStore)
        );
        injector.getInstance(ResourceStoreMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
