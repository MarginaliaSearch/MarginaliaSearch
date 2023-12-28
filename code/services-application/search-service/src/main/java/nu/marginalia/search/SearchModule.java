package nu.marginalia.search;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import nu.marginalia.LanguageModels;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.renderer.config.HandlebarsConfigurator;
import nu.marginalia.feedlot.FeedlotClient;

import java.time.Duration;

public class SearchModule extends AbstractModule {

    public void configure() {
        bind(HandlebarsConfigurator.class).to(SearchHandlebarsConfigurator.class);

        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());

        bind(WebsiteUrl.class).toInstance(new WebsiteUrl(
                System.getProperty("website-url", "https://search.marginalia.nu/")));
    }

    @Provides
    public FeedlotClient provideFeedlotClient() {
        return new FeedlotClient(
                System.getProperty("ext-svc-feedlot-host", "feedlot"),
                Integer.getInteger("ext-svc-feedlot-port", 80),
                GsonFactory.get(),
                Duration.ofMillis(250),
                Duration.ofMillis(100)
        );
    }
}
