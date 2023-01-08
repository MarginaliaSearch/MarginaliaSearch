package nu.marginalia.wmsa.edge.converting;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexLocalService;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexWriterClient;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;

import java.nio.file.Path;

public class ConverterModule extends AbstractModule {

    private final EdgeCrawlPlan plan;

    public ConverterModule(EdgeCrawlPlan plan) {
        this.plan = plan;
    }

    public void configure() {
        bind(EdgeCrawlPlan.class).toInstance(plan);

        bind(Gson.class).toInstance(createGson());

        bind(Double.class).annotatedWith(Names.named("min-document-quality")).toInstance(-15.);
        bind(Integer.class).annotatedWith(Names.named("min-document-length")).toInstance(250);
        bind(Integer.class).annotatedWith(Names.named("max-title-length")).toInstance(128);
        bind(Integer.class).annotatedWith(Names.named("max-summary-length")).toInstance(255);

        if (null != System.getProperty("local-index-path")) {
            bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(Path.of(System.getProperty("local-index-path")));
            bind(EdgeIndexWriterClient.class).to(EdgeIndexLocalService.class);
        }
        else {
            bind(EdgeIndexWriterClient.class).to(EdgeIndexClient.class);
        }


        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
