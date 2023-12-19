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
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.linkdb.LinkdbWriter;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.SearchServiceDescriptors;
import nu.marginalia.service.descriptor.ServiceDescriptors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public class LoaderModule extends AbstractModule {

    public LoaderModule() {
    }

    public void configure() {
        bind(ServiceDescriptors.class).toInstance(SearchServiceDescriptors.descriptors);

        bind(Gson.class).toProvider(this::createGson);
        bind(Path.class).annotatedWith(Names.named("local-index-path")).toInstance(Path.of(System.getProperty("local-index-path", "/vol")));
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    @Inject @Provides @Singleton
    private LinkdbWriter createLinkdbWriter(FileStorageService service) throws SQLException, IOException {

        Path dbPath = IndexLocations.getLinkdbWritePath(service).resolve("links.db");

        if (Files.exists(dbPath)) {
            Files.delete(dbPath);
        }
        return new LinkdbWriter(dbPath);
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
