package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.CrawlRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessSpawnerService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;

/** Runs a crawl on a WIDE_DOMAINS node.
 */
@Singleton
public class WideCrawlActor extends RecordActorPrototype {

    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final ActorProcessWatcher processWatcher;

    public record Initial(FileStorageId fid) implements ActorStep {}

    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Crawl(long messageId, FileStorageId fid) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial(FileStorageId fid) when fid.id() < 0 -> {
                var dataArea = storageService.allocateStorage(FileStorageType.CRAWL_DATA, "crawl-data", "Crawl data");

                yield new Initial(dataArea.id());
            }
            case Initial(FileStorageId fid) -> {
                var crawlStorage = storageService.getStorage(fid);

                if (crawlStorage == null) yield new Error("Bad storage id");
                if (crawlStorage.type() != FileStorageType.CRAWL_DATA) yield new Error("Bad storage type " + crawlStorage.type());

                long msgId = mqCrawlerOutbox.sendAsync(CrawlRequest.forFullCrawl(fid));

                yield new Crawl(msgId, fid);
            }
            case Crawl(long msgId, FileStorageId fid) -> {
                var rsp = processWatcher.waitResponse(
                        mqCrawlerOutbox,
                        ProcessSpawnerService.ProcessId.CRAWLER,
                        msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("Crawler failed");
                }

                yield new End();
            }
            default -> new End();
        };
    }

    @Override
    public String describe() {
        return "Run a time-limited crawl of the domains assigned to this node";
    }

    @Inject
    public WideCrawlActor(ActorProcessWatcher processWatcher,
                          ProcessOutboxes processOutboxes,
                          FileStorageService storageService,
                          Gson gson)
    {
        super(gson);

        this.processWatcher = processWatcher;
        this.mqCrawlerOutbox = processOutboxes.getCrawlerOutbox();
        this.storageService = storageService;
    }
}
