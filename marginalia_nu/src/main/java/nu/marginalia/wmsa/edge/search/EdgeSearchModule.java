package nu.marginalia.wmsa.edge.search;

import com.google.inject.AbstractModule;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.wmsa.configuration.WebsiteUrl;
import nu.marginalia.wmsa.configuration.WmsaHome;

public class EdgeSearchModule extends AbstractModule {

    public void configure() {
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
        bind(WebsiteUrl.class).toInstance(new WebsiteUrl(System.getProperty("website-url", "https://search.marginalia.nu/")));
    }

}
