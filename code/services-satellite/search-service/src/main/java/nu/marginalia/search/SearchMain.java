package nu.marginalia.search;

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

public class SearchMain extends MainClass {
    private final SearchService service;

    @Inject
    public SearchMain(SearchService service) {
        this.service = service;
    }

    public static void main(String... args) {

        init(ServiceId.Search, args);

        Spark.staticFileLocation("/static/search/");

        Injector injector = Guice.createInjector(
                new SearchModule(),
                new ConfigurationModule(SearchServiceDescriptors.descriptors, ServiceId.Search),
                new DatabaseModule()
        );

        injector.getInstance(SearchMain.class);
        injector.getInstance(Initialization.class).setReady();

    }
}
