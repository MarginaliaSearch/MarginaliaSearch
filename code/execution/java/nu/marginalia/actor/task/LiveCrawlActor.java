package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.IndexLocations;
import nu.marginalia.actor.ActorTimeslot;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorStateMachines;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.LiveCrawlRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessSpawnerService;
import nu.marginalia.rss.svc.FeedFetcherService;
import nu.marginalia.rss.svc.FeedsGrpcService;
import nu.marginalia.storage.FileStorageService;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Singleton
public class LiveCrawlActor extends RecordActorPrototype {
    private static final ActorTimeslot.ActorSchedule schedule = ActorTimeslot.LIVE_CRAWLER_SLOT;

    // STATES
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox mqLiveCrawlerOutbox;
    private final ExecutorActorStateMachines executorActorStateMachines;
    private final FeedFetcherService feedFetcherService;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LanguageConfiguration languageConfiguration;
    private final FileStorageService fileStorageService;

    public record Initial() implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Monitor(String checkTimeTs, String feedsHash) implements ActorStep {
        public Monitor(String feedsHash) {
            ActorTimeslot slot = schedule.nextTimeslot();

            this(slot.start().toString(), feedsHash);
        }
    }
    @Resume(behavior = ActorResumeBehavior.RESTART)
    public record LiveCrawl(String feedsHash, long msgId) implements ActorStep {
        public LiveCrawl(String feedsHash) { this(feedsHash, -1); }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> {
                yield new Monitor(feedFetcherService.getFeedDataHash());
            }
            case Monitor(String checkTimeTs, String oldHash) -> {
                Instant checkTime = Instant.parse(checkTimeTs);
                Thread.sleep(Duration.between(Instant.now(), checkTime));

                // Sleep initially in case this is during start-up
                String newHash = feedFetcherService.getFeedDataHash();

                if (!Objects.equals(newHash, oldHash)) {
                    yield new LiveCrawl(newHash);
                }
                else {
                    yield new Monitor(oldHash);
                }
            }
            case LiveCrawl(String feedsHash, long msgId) when msgId < 0 -> {
                // Clear the index journals before starting the crawl
                Path indexConstructionArea = IndexLocations.getIndexConstructionArea(fileStorageService);

                for (var languageDefinition : languageConfiguration.languages()) {
                    Path dir = IndexJournal.allocateName(indexConstructionArea, languageDefinition.isoCode());

                    List<Path> indexDirContents = new ArrayList<>();
                    try (var contentsStream = Files.list(dir)) {
                        contentsStream.filter(Files::isRegularFile).forEach(indexDirContents::add);
                    }

                    for (var junkFile: indexDirContents) {
                        Files.deleteIfExists(junkFile);
                    }
                }

                long id = mqLiveCrawlerOutbox.sendAsync(new LiveCrawlRequest());
                yield new LiveCrawl(feedsHash, id);
            }
            case LiveCrawl(String feedsHash, long msgId) -> {
                var rsp = processWatcher.waitResponse(mqLiveCrawlerOutbox, ProcessSpawnerService.ProcessId.LIVE_CRAWLER, msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("Crawler failed");
                }

                // Build the index
                executorActorStateMachines.initFrom(ExecutorActor.CONVERT_AND_LOAD, new ConvertAndLoadActor.Rerank());

                yield new Monitor(feedsHash);
            }
            default -> new Error("Unknown state");
        };
    }

    @Override
    public String describe() {
        return "Actor that polls the feeds database for changes, and triggers the live crawler when needed";
    }

    @Inject
    public LiveCrawlActor(ActorProcessWatcher processWatcher,
                          ProcessOutboxes processOutboxes,
                          FeedFetcherService feedFetcherService,
                          Gson gson,
                          ExecutorActorStateMachines executorActorStateMachines, LanguageConfiguration languageConfiguration, FileStorageService fileStorageService)
    {
        super(gson);
        this.processWatcher = processWatcher;
        this.mqLiveCrawlerOutbox = processOutboxes.getLiveCrawlerOutbox();
        this.executorActorStateMachines = executorActorStateMachines;
        this.feedFetcherService = feedFetcherService;
        this.languageConfiguration = languageConfiguration;
        this.fileStorageService = fileStorageService;
    }


}
