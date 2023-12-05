package nu.marginalia.search;

import com.google.inject.AbstractModule;
import nu.marginalia.LanguageModels;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.WmsaHome;
import nu.marginalia.renderer.config.HandlebarsConfigurator;

public class SearchModule extends AbstractModule {

    public void configure() {
        bind(HandlebarsConfigurator.class).to(SearchHandlebarsConfigurator.class);

        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());

        bind(WebsiteUrl.class).toInstance(new WebsiteUrl(
                System.getProperty("website-url", "https://search.marginalia.nu/")));
    }

}
