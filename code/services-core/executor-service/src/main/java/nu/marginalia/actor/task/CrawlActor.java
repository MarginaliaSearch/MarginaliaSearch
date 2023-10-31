package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.CrawlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class CrawlActor extends RecordActorPrototype {

    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ActorProcessWatcher processWatcher;

    public record Initial(FileStorageId storageId) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Crawl(long messageId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial (FileStorageId fid) -> {
                var storage = storageService.getStorage(fid);

                if (storage == null) yield new Error("Bad storage id");
                if (storage.type() != FileStorageType.CRAWL_SPEC) yield new Error("Bad storage type " + storage.type());

                var base = storageService.getStorageBase(FileStorageBaseType.STORAGE);
                var dataArea = storageService.allocateTemporaryStorage(
                        base,
                        FileStorageType.CRAWL_DATA,
                        "crawl-data",
                        storage.description());

                storageService.relateFileStorages(storage.id(), dataArea.id());

                // Send convert request
                long msgId = mqCrawlerOutbox.sendAsync(new CrawlRequest(List.of(fid), dataArea.id()));

                yield new Crawl(msgId);
            }
            case Crawl(long msgId) -> {
                var rsp = processWatcher.waitResponse(mqCrawlerOutbox, ProcessService.ProcessId.CRAWLER, msgId);

                if (rsp.state() != MqMessageState.OK)
                    yield new Error("Crawler failed");

                yield new End();
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Run the crawler with the given crawl spec using no previous crawl data for a reference";
    }

    @Inject
    public CrawlActor(ProcessOutboxes processOutboxes,
                      FileStorageService storageService,
                      Gson gson,
                      ActorProcessWatcher processWatcher)
    {
        super(gson);
        this.mqCrawlerOutbox = processOutboxes.getCrawlerOutbox();
        this.storageService = storageService;
        this.processWatcher = processWatcher;
    }

}
