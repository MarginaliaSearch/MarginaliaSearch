package nu.marginalia.converting;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import plan.CrawlPlan;
import nu.marginalia.model.gson.GsonFactory;

public class ConverterModule extends AbstractModule {

    private final CrawlPlan plan;

    public ConverterModule(CrawlPlan plan) {
        this.plan = plan;
    }

    public void configure() {
        bind(CrawlPlan.class).toInstance(plan);

        bind(Gson.class).toInstance(createGson());

        bind(Double.class).annotatedWith(Names.named("min-document-quality")).toInstance(-15.);
        bind(Integer.class).annotatedWith(Names.named("min-document-length")).toInstance(250);
        bind(Integer.class).annotatedWith(Names.named("max-title-length")).toInstance(128);
        bind(Integer.class).annotatedWith(Names.named("max-summary-length")).toInstance(255);

        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
