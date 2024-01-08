package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.linkdb.DomainLinkDb;
import nu.marginalia.linkdb.FileDomainLinkDb;
import nu.marginalia.linkdb.SqlDomainLinkDb;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.config.RankingSettings;
import nu.marginalia.WmsaHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static nu.marginalia.linkdb.LinkdbFileNames.*;

public class IndexModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(IndexModule.class);

    public void configure() {
    }

    @Provides
    public RankingSettings rankingSettings() {
        Path dir = WmsaHome.getHomePath().resolve("conf/ranking-settings.yaml");
        return RankingSettings.from(dir);
    }

    @Provides
    @Singleton
    public DomainLinkDb domainLinkDb (
            FileStorageService storageService,
            HikariDataSource dataSource,
            ServiceConfiguration serviceConfiguration
            ) throws IOException
    {
        Path path = IndexLocations.getLinkdbLivePath(storageService).resolve(DOMAIN_LINKS_FILE_NAME);

        if (Files.exists(path)) {
            logger.info("Using file domain link db {}", path);
            return new FileDomainLinkDb(path);
        }
        else {
            logger.warn("Using legacy sql domain link db");
            return new SqlDomainLinkDb(path, dataSource, serviceConfiguration);
        }
    }

    @Provides
    @Singleton
    @Named("docdb-file")
    public Path linkdbPath(FileStorageService storageService) throws IOException {
        // Migrate from old location
        Path migrationMarker = IndexLocations.getLinkdbLivePath(storageService).resolve("migrated-links.db-to-documents.db");
        Path oldPath = IndexLocations.getLinkdbLivePath(storageService).resolve(DEPRECATED_LINKDB_FILE_NAME);
        Path newPath = IndexLocations.getLinkdbLivePath(storageService).resolve(DOCDB_FILE_NAME);

        if (Files.exists(oldPath) && !Files.exists(newPath) && !Files.exists(migrationMarker)) {
            logger.info("Migrating {} to {}", oldPath, newPath);

            Files.move(oldPath, newPath);
            Files.createFile(migrationMarker);
        }

        return newPath;
    }

    @Provides
    @Singleton
    @Named("domain-linkdb-file")
    public Path domainLinkDbFile(FileStorageService storageService) throws SQLException {
        return IndexLocations.getLinkdbLivePath(storageService).resolve(DOMAIN_LINKS_FILE_NAME);
    }
}
