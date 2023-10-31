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
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.CrawlRequest;
import nu.marginalia.svc.DomainListRefreshService;

import java.nio.file.Files;

@Singleton
public class RecrawlActor extends RecordActorPrototype {

    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final DomainListRefreshService refreshService;
    private final ActorProcessWatcher processWatcher;


    public record Initial(FileStorageId storageId) implements ActorStep {}
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Crawl(long messageId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial (FileStorageId fid) -> {
                var crawlStorage = storageService.getStorage(fid);

                if (crawlStorage == null) yield new Error("Bad storage id");
                if (crawlStorage.type() != FileStorageType.CRAWL_DATA) yield new Error("Bad storage type " + crawlStorage.type());

                Files.deleteIfExists(crawlStorage.asPath().resolve("crawler.log"));

                refreshService.synchronizeDomainList();

                long id = mqCrawlerOutbox.sendAsync(new CrawlRequest(null, fid));

                yield new Crawl(id);
            }
            case Crawl (long msgId) -> {
                var rsp = processWatcher.waitResponse(mqCrawlerOutbox, ProcessService.ProcessId.CRAWLER, msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("Crawler failed");
                }
                else {
                    yield new End();
                }
            }
            default -> new End();
        };
    }

    @Override
    public String describe() {
        return "Run the crawler with the given crawl spec using previous crawl data for a reference";
    }

    @Inject
    public RecrawlActor(ActorProcessWatcher processWatcher,
                        ProcessOutboxes processOutboxes,
                        FileStorageService storageService,
                        DomainListRefreshService refreshService,
                        Gson gson)
    {
        super(gson);
        this.processWatcher = processWatcher;
        this.mqCrawlerOutbox = processOutboxes.getCrawlerOutbox();
        this.storageService = storageService;
        this.refreshService = refreshService;
    }

}
