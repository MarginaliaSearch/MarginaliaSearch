package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.IndexLocations;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorStateMachines;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.LiveCrawlRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.List;

@Singleton
public class LiveCrawlActor extends RecordActorPrototype {

    // STATES
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox mqLiveCrawlerOutbox;
    private final FileStorageService storageService;
    private final ExecutorActorStateMachines executorActorStateMachines;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Initial() implements ActorStep {}
    public record LiveCrawl(FileStorageId id, long msgId) implements ActorStep {
        public LiveCrawl(FileStorageId id) { this(id, -1); }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        logger.info("{}", self);
        return switch (self) {
            case Initial() -> {
                // clear the output directory of the loader from any debris from partial jobs that have been aborted
                Files.list(IndexLocations.getIndexConstructionArea(storageService)).forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            FileUtils.deleteDirectory(path.toFile());
                        }
                        else if (Files.isRegularFile(path)) {
                            Files.delete(path);
                        }
                    } catch (Exception e) {
                        logger.error("Error clearing staging area", e);
                    }
                });


                List<FileStorageId> activeCrawlData = storageService.getActiveFileStorages(FileStorageType.CRAWL_DATA);
                if (activeCrawlData.isEmpty()) {
                    var storage = storageService.allocateStorage(FileStorageType.CRAWL_DATA, "crawl-data", "Crawl data");
                    yield new LiveCrawl(storage.id());
                } else {
                    yield new LiveCrawl(activeCrawlData.getFirst());
                }
            }
            case LiveCrawl(FileStorageId storageId, long msgId) when msgId < 0 -> {
                long id = mqLiveCrawlerOutbox.sendAsync(new LiveCrawlRequest(storageId));
                yield new LiveCrawl(storageId, id);
            }
            case LiveCrawl(FileStorageId storageId, long msgId) -> {
                var rsp = processWatcher.waitResponse(mqLiveCrawlerOutbox, ProcessService.ProcessId.LIVE_CRAWLER, msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("Crawler failed");
                }

                executorActorStateMachines.initFrom(ExecutorActor.CONVERT_AND_LOAD, new ConvertAndLoadActor.Rerank());

                yield new End();
            }
            default -> new Error("Unknown state");
        };
    }

    @Override
    public String describe() {
        return "Process a set of crawl data and then load it into the database.";
    }

    @Inject
    public LiveCrawlActor(ActorProcessWatcher processWatcher,
                          ProcessOutboxes processOutboxes,
                          FileStorageService storageService,
                          Gson gson,
                          ExecutorActorStateMachines executorActorStateMachines)
    {
        super(gson);
        this.processWatcher = processWatcher;
        this.mqLiveCrawlerOutbox = processOutboxes.getLiveCrawlerOutbox();
        this.storageService = storageService;
        this.executorActorStateMachines = executorActorStateMachines;

    }


}
