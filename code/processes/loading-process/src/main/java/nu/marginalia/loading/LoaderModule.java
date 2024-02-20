package nu.marginalia.loading;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.IndexLocations;
import nu.marginalia.linkdb.dlinks.DomainLinkDbWriter;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.model.gson.GsonFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static nu.marginalia.linkdb.LinkdbFileNames.DOMAIN_LINKS_FILE_NAME;

public class LoaderModule extends AbstractModule {

    public LoaderModule() {
    }

    public void configure() {
        bind(Gson.class).toProvider(this::createGson);
        bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(Path.of(System.getProperty("local-index-path", "/vol")));
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    @Inject @Provides @Singleton
    private DocumentDbWriter createLinkdbWriter(FileStorageService service) throws SQLException, IOException {
        // Migrate
        Path dbPath = IndexLocations.getLinkdbWritePath(service).resolve(DOCDB_FILE_NAME);

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }
        return new DocumentDbWriter(dbPath);
    }

    @Inject @Provides @Singleton
    private DomainLinkDbWriter createDomainLinkdbWriter(FileStorageService service) throws SQLException, IOException {

        Path dbPath = IndexLocations.getLinkdbWritePath(service).resolve(DOMAIN_LINKS_FILE_NAME);

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }

        return new DomainLinkDbWriter(dbPath);
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
