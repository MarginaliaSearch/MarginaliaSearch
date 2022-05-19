package nu.marginalia.wmsa.data_store;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.wmsa.configuration.MainClass;
import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.ConfigurationModule;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.edge.index.EdgeTablesModule;

import java.io.IOException;

public class DataStoreMain extends MainClass {
    private final DataStoreService service;

    @Inject
    public DataStoreMain(DataStoreService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceDescriptor.DATA_STORE, args);
        Injector injector = Guice.createInjector(
                new DataStoreModule(),
                new EdgeTablesModule(),
                new DatabaseModule(),
                new ConfigurationModule()
        );
        injector.getInstance(DataStoreMain.class);

        injector.getInstance(Initialization.class).setReady();
    }
}
