package nu.marginalia.wmsa.edge.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.index.model.RankingSettings;

import java.nio.file.Path;

public class EdgeIndexModule extends AbstractModule {



    public void configure() {
        bind(Long.class).annotatedWith(Names.named("edge-dictionary-hash-map-size")).toInstance(1L << 31);
    }

    @Provides
    public RankingSettings rankingSettings() {
        Path dir = WmsaHome.get().resolve("conf/ranking-settings.yaml");
        return RankingSettings.from(dir);
    }

}
