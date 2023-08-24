package nu.marginalia.search;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.LanguageModels;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.WmsaHome;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageType;

import java.nio.file.Path;
import java.sql.SQLException;

public class SearchModule extends AbstractModule {

    public void configure() {
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
        bind(WebsiteUrl.class).toInstance(new WebsiteUrl(System.getProperty("website-url", "https://search.marginalia.nu/")));
    }

    @Provides
    @Singleton
    @Named("linkdb-file")
    public Path linkdbPath(FileStorageService storageService) throws SQLException {
        return storageService
                .getStorageByType(FileStorageType.LINKDB_LIVE)
                .asPath()
                .resolve("links.db");
    }

}
