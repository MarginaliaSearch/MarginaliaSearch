package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.config.RankingSettings;
import nu.marginalia.WmsaHome;
import nu.marginalia.service.control.ServiceEventLog;

import java.nio.file.Path;

public class IndexModule extends AbstractModule {



    public void configure() {
    }

    @Provides
    public RankingSettings rankingSettings() {
        Path dir = WmsaHome.getHomePath().resolve("conf/ranking-settings.yaml");
        return RankingSettings.from(dir);
    }

}
