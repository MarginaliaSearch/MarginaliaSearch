package nu.marginalia.loading;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.LanguageModels;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.WmsaHome;
import plan.CrawlPlan;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.SearchServiceDescriptors;
import nu.marginalia.service.descriptor.ServiceDescriptors;

import java.nio.file.Path;
import java.util.UUID;

public class LoaderModule extends AbstractModule {

    private final CrawlPlan plan;

    public LoaderModule(CrawlPlan plan) {
        this.plan = plan;
    }

    public void configure() {
        bind(CrawlPlan.class).toInstance(plan);

        bind(ServiceDescriptors.class).toInstance(SearchServiceDescriptors.descriptors);
        bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration("loader", 0, UUID.randomUUID()));

        bind(Gson.class).toProvider(this::createGson);

        bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(Path.of(System.getProperty("local-index-path", "/vol")));
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
