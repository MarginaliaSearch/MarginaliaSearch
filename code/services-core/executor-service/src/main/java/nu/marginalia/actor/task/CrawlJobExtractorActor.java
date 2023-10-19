package nu.marginalia.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.crawlspec.CrawlSpecFileNames;
import nu.marginalia.db.DbDomainStatsExportMultitool;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static nu.marginalia.crawlspec.CrawlSpecGenerator.*;

@Singleton
public class CrawlJobExtractorActor extends AbstractActorPrototype {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // STATES

    public static final String CREATE_FROM_LINK = "CREATE_FROM_LINK";
    public static final String END = "END";
    private final FileStorageService fileStorageService;
    @Inject
    public CrawlJobExtractorActor(ActorStateFactory stateFactory,
                                  FileStorageService fileStorageService
                                  ) {
        super(stateFactory);
        this.fileStorageService = fileStorageService;
    }

    public record CrawlJobExtractorArguments(String description) { }
    public record CrawlJobExtractorArgumentsWithURL(String description, String url) { }

    @Override
    public String describe() {
        return "Run the crawler job extractor process";
    }

    @ActorState(name = CREATE_FROM_LINK, next = END,
            resume = ActorResumeBehavior.ERROR,
            description = """
                        Download a list of URLs as provided, 
                        and then spawn a CrawlJobExtractor process, 
                        then wait for it to finish.
                        """
    )
    public void createFromFromLink(CrawlJobExtractorArgumentsWithURL arg) throws Exception {
        if (arg == null) {
            error("This actor requires a CrawlJobExtractorArgumentsWithURL argument");
        }

        var base = fileStorageService.getStorageBase(FileStorageBaseType.STORAGE);
        var storage = fileStorageService.allocateTemporaryStorage(base, FileStorageType.CRAWL_SPEC, "crawl-spec", arg.description());

        Path urlsTxt = storage.asPath().resolve("urls.txt");

        try (var os = Files.newOutputStream(urlsTxt, StandardOpenOption.CREATE_NEW);
             var is = new URL(arg.url()).openStream())
        {
            is.transferTo(os);
        }
        catch (Exception ex) {
            fileStorageService.flagFileForDeletion(storage.id());
            error("Error downloading " + arg.url());
        }

        final Path path = CrawlSpecFileNames.resolve(storage);

        generateCrawlSpec(
                path,
                DomainSource.fromFile(urlsTxt),
                KnownUrlsCountSource.fixed(200),
                KnownUrlsListSource.justIndex()
        );
    }

}
