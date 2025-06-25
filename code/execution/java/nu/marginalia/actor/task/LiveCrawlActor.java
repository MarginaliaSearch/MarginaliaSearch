package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.IndexLocations;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorStateMachines;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.LiveCrawlRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

@Singleton
public class LiveCrawlActor extends RecordActorPrototype {

    // STATES
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox mqLiveCrawlerOutbox;
    private final ExecutorActorStateMachines executorActorStateMachines;
    private final FeedsClient feedsClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final FileStorageService fileStorageService;

    public record Initial() implements ActorStep {}
    public record Monitor(String feedsHash) implements ActorStep {}
    public record LiveCrawl(String feedsHash, long msgId) implements ActorStep {
        public LiveCrawl(String feedsHash) { this(feedsHash, -1); }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial() -> {
                yield new Monitor("-");
            }
            case Monitor(String feedsHash) -> {
                // Sleep initially in case this is during start-up
                for (;;) {
                    try {
                        Thread.sleep(Duration.ofMinutes(15));
                        String currentHash = feedsClient.getFeedDataHash();
                        if (!Objects.equals(currentHash, feedsHash)) {
                            yield new LiveCrawl(currentHash);
                        }
                    }
                    catch (RuntimeException ex) {
                        logger.error("Failed to fetch feed data hash");
                    }
                }
            }
            case LiveCrawl(String feedsHash, long msgId) when msgId < 0 -> {
                // Clear the index journal before starting the crawl
                Path indexJournalLocation = IndexLocations.getIndexConstructionArea(fileStorageService).resolve("index-journal");
                if (Files.isDirectory(indexJournalLocation)) {
                    FileUtils.deleteDirectory(indexJournalLocation.toFile());
                }

                long id = mqLiveCrawlerOutbox.sendAsync(new LiveCrawlRequest());
                yield new LiveCrawl(feedsHash, id);
            }
            case LiveCrawl(String feedsHash, long msgId) -> {
                var rsp = processWatcher.waitResponse(mqLiveCrawlerOutbox, ProcessService.ProcessId.LIVE_CRAWLER, msgId);

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
                          FeedsClient feedsClient,
                          Gson gson,
                          ExecutorActorStateMachines executorActorStateMachines, FileStorageService fileStorageService)
    {
        super(gson);
        this.processWatcher = processWatcher;
        this.mqLiveCrawlerOutbox = processOutboxes.getLiveCrawlerOutbox();
        this.executorActorStateMachines = executorActorStateMachines;
        this.feedsClient = feedsClient;
        this.fileStorageService = fileStorageService;
    }


}
