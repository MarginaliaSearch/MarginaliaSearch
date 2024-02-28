package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.renderer.config.DefaultHandlebarsConfigurator;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

public class QueryModule extends AbstractModule {
    public void configure() {
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
        bind(Gson.class).toProvider(GsonFactory::get);
        bind(HandlebarsConfigurator.class).to(DefaultHandlebarsConfigurator.class);
    }
}
