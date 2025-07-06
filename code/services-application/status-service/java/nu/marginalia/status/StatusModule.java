package nu.marginalia.status;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.WmsaHome;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

public class StatusModule extends AbstractModule {

    public void configure() {
        bind(HandlebarsConfigurator.class).toInstance(handlebars -> {});

        bind(String.class)
                .annotatedWith(Names.named("statusDbPath"))
                .toInstance(WmsaHome.getDataPath().resolve("status-service.db").toString());
        bind(String.class)
                .annotatedWith(Names.named("apiTestQuery"))
                .toInstance(System.getProperty("status-service.api-query",
                        "https://api.marginalia.nu/public/search/plato"));
        bind(String.class)
                .annotatedWith(Names.named("searchEngineTestQuery"))
                .toInstance(System.getProperty("status-service.public-query",
                        "https://old-search.marginalia.nu/search?query=plato&ref=marginalia-automatic-metrics"));
    }
}
