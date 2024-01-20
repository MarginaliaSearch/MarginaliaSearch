package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.crawlspec.CrawlSpecFileNames;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static nu.marginalia.crawlspec.CrawlSpecGenerator.*;

@Singleton
public class CrawlJobExtractorActor extends RecordActorPrototype {

    private static final int INITIAL_URLS_PER_DOMAIN = Integer.getInteger("crawler.initialUrlsPerDomain", 250);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final FileStorageService fileStorageService;

    @Inject
    public CrawlJobExtractorActor(Gson gson,
                                  FileStorageService fileStorageService
                                  ) {
        super(gson);
        this.fileStorageService = fileStorageService;
    }

    public record CreateFromUrl(String description, String url) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case CreateFromUrl(String description, String url) -> {
                var base = fileStorageService.getStorageBase(FileStorageBaseType.STORAGE);
                var storage = fileStorageService.allocateTemporaryStorage(base, FileStorageType.CRAWL_SPEC, "crawl-spec", description);

                Path urlsTxt = storage.asPath().resolve("urls.txt");

                try {
                    downloadToFile(url, urlsTxt);
                }
                catch (IOException ex) {
                    fileStorageService.flagFileForDeletion(storage.id());
                    yield new Error("Error downloading " + url);
                }

                final Path path = CrawlSpecFileNames.resolve(storage);

                generateCrawlSpec(
                        path,
                        DomainSource.fromFile(urlsTxt),
                        KnownUrlsCountSource.fixed(INITIAL_URLS_PER_DOMAIN),
                        KnownUrlsListSource.justIndex()
                );

                yield new End();
            }
            default -> new Error();
        };
    }

    private void downloadToFile(String url, Path urlsTxt) throws IOException {
        try (var os = Files.newOutputStream(urlsTxt, StandardOpenOption.CREATE_NEW);
             var is = new URL(url).openStream())
        {
            is.transferTo(os);
        }
    }

    @Override
    public String describe() {
        return "Run the crawler job extractor process";
    }

}
