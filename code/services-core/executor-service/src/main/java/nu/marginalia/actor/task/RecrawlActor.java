package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.CrawlRequest;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;

@Singleton
public class RecrawlActor extends AbstractActorPrototype {

    // STATES

    public static final String INITIAL = "INITIAL";
    public static final String CRAWL = "CRAWL";
    public static final String CRAWL_WAIT = "CRAWL-WAIT";
    public static final String END = "END";
    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final Gson gson;
    private final ActorProcessWatcher processWatcher;


    @AllArgsConstructor @With @NoArgsConstructor
    public static class RecrawlMessage {
        /** The storage where the crawl data will be stored.  If this contains existing crawl
         * data, it will be consulted for e.g. e-tag comparisons.
        */
        @NotNull
        public FileStorageId crawlStorageId;

        public long crawlerMsgId = 0L;
    };

    @Override
    public String describe() {
        return "Run the crawler with the given crawl spec using previous crawl data for a reference";
    }

    public static RecrawlMessage recrawlFromCrawlDataAndCrawlSpec(FileStorageId crawlData) {
        return new RecrawlMessage(crawlData, 0L);
    }

    @Inject
    public RecrawlActor(ActorStateFactory stateFactory,
                        ActorProcessWatcher processWatcher,
                        ProcessOutboxes processOutboxes,
                        FileStorageService storageService,
                        Gson gson
                                   )
    {
        super(stateFactory);
        this.processWatcher = processWatcher;
        this.mqCrawlerOutbox = processOutboxes.getCrawlerOutbox();
        this.storageService = storageService;
        this.gson = gson;
    }

    @ActorState(name = INITIAL,
                next = CRAWL,
                description = """
                    Validate the input and transition to CRAWL
                    """)
    public RecrawlMessage init(RecrawlMessage recrawlMessage) throws Exception {
        if (null == recrawlMessage) {
            error("This Actor requires a message as an argument");
        }

        var crawlStorage = storageService.getStorage(recrawlMessage.crawlStorageId);

        if (crawlStorage == null) error("Bad storage id");
        if (crawlStorage.type() != FileStorageType.CRAWL_DATA) error("Bad storage type " + crawlStorage.type());

        Files.deleteIfExists(crawlStorage.asPath().resolve("crawler.log"));

        return recrawlMessage;
    }

    @ActorState(name = CRAWL,
                next = CRAWL_WAIT,
                resume = ActorResumeBehavior.ERROR,
                description = """
                        Send a crawl request to the crawler and transition to CRAWL_WAIT.
                        """
    )
    public RecrawlMessage crawl(RecrawlMessage recrawlMessage) throws Exception {
        // Pre-send crawl request

        var request = new CrawlRequest(null, recrawlMessage.crawlStorageId);
        long id = mqCrawlerOutbox.sendAsync(CrawlRequest.class.getSimpleName(), gson.toJson(request));

        return recrawlMessage.withCrawlerMsgId(id);
    }

    @ActorState(
            name = CRAWL_WAIT,
            next = END,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Wait for the crawler to finish retrieving the data.
                    """
    )
    public RecrawlMessage crawlerWait(RecrawlMessage recrawlMessage) throws Exception {
        var rsp = processWatcher.waitResponse(mqCrawlerOutbox, ProcessService.ProcessId.CRAWLER, recrawlMessage.crawlerMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Crawler failed");

        return recrawlMessage;
    }

}
