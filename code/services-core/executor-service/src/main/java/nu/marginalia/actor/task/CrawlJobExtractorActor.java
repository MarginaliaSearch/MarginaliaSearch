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

    public static final String CREATE_FROM_DB = "CREATE_FROM_DB";
    public static final String CREATE_FROM_LINK = "CREATE_FROM_LINK";
    public static final String END = "END";
    private final FileStorageService fileStorageService;
    private final HikariDataSource dataSource;

    @Inject
    public CrawlJobExtractorActor(ActorStateFactory stateFactory,
                                  FileStorageService fileStorageService,
                                  HikariDataSource dataSource
                                  ) {
        super(stateFactory);
        this.fileStorageService = fileStorageService;
        this.dataSource = dataSource;
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


    @ActorState(name = CREATE_FROM_DB, next = END,
                resume = ActorResumeBehavior.ERROR,
                description = """
                        Spawns a CrawlJobExtractor process that loads data from the link database, and wait for it to finish.
                        """
    )
    public void createFromDB(CrawlJobExtractorArguments arg) throws Exception {
        if (arg == null) {
            error("This actor requires a CrawlJobExtractorArguments argument");
        }

        var base = fileStorageService.getStorageBase(FileStorageBaseType.STORAGE);
        var storage = fileStorageService.allocateTemporaryStorage(base, FileStorageType.CRAWL_SPEC, "crawl-spec", arg.description());

        final Path path = CrawlSpecFileNames.resolve(storage);

        try (var dbTools = new DbDomainStatsExportMultitool(dataSource)) {
            generateCrawlSpec(
                    path,
                    DomainSource.combined(
                            DomainSource.knownUrlsFromDb(dbTools),
                            DomainSource.fromCrawlQueue(dbTools)
                    ),
                    KnownUrlsCountSource.fromDb(dbTools, 200),
                    KnownUrlsListSource.justIndex() // TODO: hook in linkdb maybe?
            );
        }

    }

}
