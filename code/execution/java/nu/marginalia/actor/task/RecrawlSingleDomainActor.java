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

@Singleton
public class RecrawlSingleDomainActor extends RecordActorPrototype {

    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final ActorProcessWatcher processWatcher;

    /** Initial step
     * @param storageId - the id of the storage to recrawl
     * @param targetDomainName - domain to be recrawled
     */
    public record Initial(FileStorageId storageId, String targetDomainName) implements ActorStep {}

    /** The action step */
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Crawl(long messageId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial (FileStorageId fid, String targetDomainName) -> {
                var crawlStorage = storageService.getStorage(fid);

                if (crawlStorage == null) yield new Error("Bad storage id");
                if (crawlStorage.type() != FileStorageType.CRAWL_DATA) yield new Error("Bad storage type " + crawlStorage.type());

                long id = mqCrawlerOutbox.sendAsync(
                        CrawlRequest.forSingleDomain(targetDomainName, fid)
                );

                yield new Crawl(id);
            }
            case Crawl (long msgId) -> {
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
        return "Run the crawler only re-fetching a single domain";
    }

    @Inject
    public RecrawlSingleDomainActor(ActorProcessWatcher processWatcher,
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
