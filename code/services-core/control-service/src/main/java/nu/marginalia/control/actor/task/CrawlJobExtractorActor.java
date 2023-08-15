package nu.marginalia.control.actor.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.control.svc.ControlFileStorageService;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorResumeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class CrawlJobExtractorActor extends AbstractActorPrototype {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // STATES

    public static final String CREATE_FROM_DB = "CREATE_FROM_DB";
    public static final String CREATE_FROM_LINK = "CREATE_FROM_LINK";
    public static final String END = "END";
    private final ProcessService processService;
    private final FileStorageService fileStorageService;
    private final ControlFileStorageService controlFileStorageService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public CrawlJobExtractorActor(ActorStateFactory stateFactory,
                                  ProcessService processService,
                                  FileStorageService fileStorageService,
                                  ControlFileStorageService controlFileStorageService
                                  ) {
        super(stateFactory);
        this.processService = processService;
        this.fileStorageService = fileStorageService;
        this.controlFileStorageService = controlFileStorageService;
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

        var base = fileStorageService.getStorageBase(FileStorageBaseType.SLOW);
        var storage = fileStorageService.allocateTemporaryStorage(base, FileStorageType.CRAWL_SPEC, "crawl-spec", arg.description());

        Path urlsTxt = storage.asPath().resolve("urls.txt");

        try (var os = Files.newOutputStream(urlsTxt, StandardOpenOption.CREATE_NEW);
             var is = new URL(arg.url()).openStream())
        {
            is.transferTo(os);
        }
        catch (Exception ex) {
            controlFileStorageService.flagFileForDeletion(storage.id());
            error("Error downloading " + arg.url());
        }

        final Path path = storage.asPath();

        run(storage, path.resolve("crawler.spec").toString(),
                "-f", urlsTxt.toString());
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

        var base = fileStorageService.getStorageBase(FileStorageBaseType.SLOW);
        var storage = fileStorageService.allocateTemporaryStorage(base, FileStorageType.CRAWL_SPEC, "crawl-spec", arg.description());

        final Path path = storage.asPath();

        run(storage,
                path.resolve("crawler.spec").toString());
    }

    private void run(FileStorage storage, String... args) throws Exception {

        AtomicBoolean hasError = new AtomicBoolean(false);
        var future = executor.submit(() -> {
            try {
                processService.trigger(ProcessService.ProcessId.CRAWL_JOB_EXTRACTOR,
                        args);
            }
            catch (Exception ex) {
                logger.warn("Error in creating crawl job", ex);
                hasError.set(true);
            }
        });
        future.get();

        if (hasError.get()) {
            controlFileStorageService.flagFileForDeletion(storage.id());
            error("Error triggering adjacency calculation");
        }

    }

}
