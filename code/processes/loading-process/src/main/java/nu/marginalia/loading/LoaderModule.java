package nu.marginalia.loading;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import nu.marginalia.LanguageModels;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.WmsaHome;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.linkdb.LinkdbStatusWriter;
import nu.marginalia.linkdb.LinkdbWriter;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.SearchServiceDescriptors;
import nu.marginalia.service.descriptor.ServiceDescriptors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

public class LoaderModule extends AbstractModule {

    public LoaderModule() {
    }

    public void configure() {
        bind(ServiceDescriptors.class).toInstance(SearchServiceDescriptors.descriptors);
        bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration("loader", 0, UUID.randomUUID()));

        bind(Gson.class).toProvider(this::createGson);
        bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(Path.of(System.getProperty("local-index-path", "/vol")));
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    @Inject @Provides @Singleton
    private LinkdbWriter createLinkdbWriter(FileStorageService service) throws SQLException, IOException {
        var storage = service.getStorageByType(FileStorageType.LINKDB_STAGING);
        Path dbPath = storage.asPath().resolve("links.db");

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }
        return new LinkdbWriter(dbPath);
    }

    @Inject @Provides @Singleton
    private LinkdbStatusWriter createLinkdbStatusWriter(FileStorageService service) throws SQLException, IOException {
        var storage = service.getStorageByType(FileStorageType.LINKDB_STAGING);
        Path dbPath = storage.asPath().resolve("urlstatus.db");

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }
        return new LinkdbStatusWriter(dbPath);
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
