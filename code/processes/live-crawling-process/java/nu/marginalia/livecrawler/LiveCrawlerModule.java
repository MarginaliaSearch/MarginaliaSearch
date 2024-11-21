package nu.marginalia.livecrawler;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import nu.marginalia.IndexLocations;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;

public class LiveCrawlerModule extends AbstractModule {

    public void configure() {
        bind(UserAgent.class).toInstance(WmsaHome.getUserAgent());
        bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(Path.of(System.getProperty("local-index-path", "/vol")));
    }

    @Inject
    @Provides @Singleton
    private DocumentDbWriter createLinkdbWriter(FileStorageService service) throws SQLException, IOException {
        // Migrate
        Path dbPath = IndexLocations.getLinkdbWritePath(service).resolve(DOCDB_FILE_NAME);

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }
        return new DocumentDbWriter(dbPath);
    }

    @Singleton
    @Provides
    public ServiceConfiguration provideServiceConfiguration(ProcessConfiguration processConfiguration) {
        return new ServiceConfiguration(ServiceId.NOT_A_SERVICE, processConfiguration.node(), null, null, -1, UUID.randomUUID());
    }
}
