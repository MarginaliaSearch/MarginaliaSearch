package nu.marginalia.search;

import com.google.inject.AbstractModule;
import nu.marginalia.LanguageModels;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.WmsaHome;

public class SearchModule extends AbstractModule {

    public void configure() {
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
        bind(WebsiteUrl.class).toInstance(new WebsiteUrl(System.getProperty("website-url", "https://search.marginalia.nu/")));
    }

}
