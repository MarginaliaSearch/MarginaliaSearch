package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorStateMachines;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.CrawlRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.svc.DomainListRefreshService;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class CrawlActor extends RecordActorPrototype {

    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final DomainListRefreshService refreshService;
    private final ActorProcessWatcher processWatcher;
    private final ExecutorActorStateMachines executorActorStateMachines;

    /** Initial step
     * @param storageId - the id of the storage to recrawl
     * @param cascadeLoad - whether to automatically start the convert and load actor after the crawl
     */
    public record Initial(FileStorageId storageId, boolean cascadeLoad) implements ActorStep {}

    /** The action step */
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Crawl(long messageId, FileStorageId fid, boolean cascadeLoad) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial (FileStorageId fid, boolean cascadeLoad) when fid.id() < 0 -> {
                // Allocate a new storage area if we aren't given one
                var dataArea = storageService.allocateStorage(
                        FileStorageType.CRAWL_DATA,
                        "crawl-data",
                        "Crawl data");

                // Now we can jump to the main track
                yield new Initial(dataArea.id(), cascadeLoad);
            }
            case Initial (FileStorageId fid, boolean cascadeLoad) -> {
                var crawlStorage = storageService.getStorage(fid);

                if (crawlStorage == null) yield new Error("Bad storage id");
                if (crawlStorage.type() != FileStorageType.CRAWL_DATA) yield new Error("Bad storage type " + crawlStorage.type());

                Path crawlLogPath = crawlStorage.asPath().resolve("crawler.log");
                if (Files.exists(crawlLogPath)) {
                    // Save the old crawl log
                    Path crawlLogBackup = crawlStorage.asPath().resolve("crawler.log-" + System.currentTimeMillis());
                    Files.move(crawlLogPath, crawlLogBackup);
                }

                refreshService.synchronizeDomainList();

                long id = mqCrawlerOutbox.sendAsync(CrawlRequest.forFullCrawl(fid));

                yield new Crawl(id, fid, cascadeLoad);
            }
            case Crawl (long msgId, FileStorageId fid, boolean cascadeLoad) -> {
                var rsp = processWatcher.waitResponse(
                        mqCrawlerOutbox,
                        ProcessService.ProcessId.CRAWLER,
                        msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("Crawler failed");
                }

                if (cascadeLoad) {
                    // Spawn the convert and load actor
                    executorActorStateMachines.initFrom(ExecutorActor.CONVERT_AND_LOAD,
                            new ConvertAndLoadActor.Initial(fid));
                }

                yield new End();
            }
            default -> new End();
        };
    }

    @Override
    public String describe() {
        return "Run the crawler with the given crawl spec using previous crawl data for a reference";
    }

    @Inject
    public CrawlActor(ActorProcessWatcher processWatcher,
                      ProcessOutboxes processOutboxes,
                      FileStorageService storageService,
                      DomainListRefreshService refreshService,
                      ExecutorActorStateMachines executorActorStateMachines,
                      Gson gson)
    {
        super(gson);

        this.processWatcher = processWatcher;
        this.mqCrawlerOutbox = processOutboxes.getCrawlerOutbox();
        this.storageService = storageService;
        this.refreshService = refreshService;
        this.executorActorStateMachines = executorActorStateMachines;
    }

}
