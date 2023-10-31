package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.config.RankingSettings;
import nu.marginalia.WmsaHome;

import java.nio.file.Path;
import java.sql.SQLException;

public class IndexModule extends AbstractModule {



    public void configure() {
    }

    @Provides
    public RankingSettings rankingSettings() {
        Path dir = WmsaHome.getHomePath().resolve("conf/ranking-settings.yaml");
        return RankingSettings.from(dir);
    }


    @Provides
    @Singleton
    @Named("linkdb-file")
    public Path linkdbPath(FileStorageService storageService) throws SQLException {
        return IndexLocations.getLinkdbLivePath(storageService).resolve("links.db");
    }
}
