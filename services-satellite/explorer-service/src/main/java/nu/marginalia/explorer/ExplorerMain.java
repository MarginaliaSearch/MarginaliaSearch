package nu.marginalia.explorer;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.SearchServiceDescriptors;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ConfigurationModule;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.server.Initialization;
import spark.Spark;

public class ExplorerMain extends MainClass {
    final ExplorerService service;

    @Inject
    public ExplorerMain(ExplorerService service) {
        this.service = service;
    }

    public static void main(String... args) {
        init(ServiceId.Explorer, args);

        Spark.staticFileLocation("/static/explore/");

        Injector injector = Guice.createInjector(
                new ConfigurationModule(SearchServiceDescriptors.descriptors, ServiceId.Explorer),
                new DatabaseModule()
        );

        injector.getInstance(ExplorerMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
