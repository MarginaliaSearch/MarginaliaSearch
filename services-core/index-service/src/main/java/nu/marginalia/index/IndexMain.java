package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.SearchServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ConfigurationModule;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.server.Initialization;

public class IndexMain extends MainClass {
    private final IndexService service;

    @Inject
    public IndexMain(IndexService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Index, args);

        Injector injector = Guice.createInjector(
                new IndexTablesModule(),
                new IndexModule(),
                new DatabaseModule(),
                new ConfigurationModule(SearchServiceDescriptors.descriptors, ServiceId.Index)
        );

        injector.getInstance(IndexMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
