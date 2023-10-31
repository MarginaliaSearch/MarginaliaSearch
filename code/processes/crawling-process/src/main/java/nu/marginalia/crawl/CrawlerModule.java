package nu.marginalia.crawl;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.SneakyThrows;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.model.gson.GsonFactory;

import java.util.UUID;

public class CrawlerModule extends AbstractModule {
    @SneakyThrows
    public void configure() {
        bind(Gson.class).toInstance(createGson());
        bind(UserAgent.class).toInstance(WmsaHome.getUserAgent());
    }

    private Gson createGson() {
        return GsonFactory.get();
    }
}
