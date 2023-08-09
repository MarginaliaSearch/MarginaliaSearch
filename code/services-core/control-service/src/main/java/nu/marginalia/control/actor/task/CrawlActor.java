package nu.marginalia.control.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.control.process.ProcessOutboxes;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.crawling.CrawlRequest;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CrawlActor extends AbstractStateGraph {

    // STATES

    public static final String INITIAL = "INITIAL";
    public static final String CRAWL = "CRAWL";
    public static final String CRAWL_WAIT = "CRAWL-WAIT";
    public static final String END = "END";
    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final Gson gson;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ActorProcessWatcher processWatcher;


    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId crawlSpecId = null;
        public FileStorageId crawlStorageId = null;
        public long crawlerMsgId = 0L;
    };

    @Inject
    public CrawlActor(StateFactory stateFactory,
                      ProcessOutboxes processOutboxes,
                      FileStorageService storageService,
                      Gson gson,
                      ActorProcessWatcher processWatcher)
    {
        super(stateFactory);
        this.mqCrawlerOutbox = processOutboxes.getCrawlerOutbox();
        this.storageService = storageService;
        this.gson = gson;
        this.processWatcher = processWatcher;
    }

    @GraphState(name = INITIAL,
                next = CRAWL,
                description = """
                    Validate the input and transition to CRAWL
                    """)
    public Message init(FileStorageId crawlStorageId) throws Exception {
        if (null == crawlStorageId) {
            error("This Actor requires a FileStorageId to be passed in as a parameter to INITIAL");
        }

        var storage = storageService.getStorage(crawlStorageId);

        if (storage == null) error("Bad storage id");
        if (storage.type() != FileStorageType.CRAWL_SPEC) error("Bad storage type " + storage.type());

        return new Message().withCrawlSpecId(crawlStorageId);
    }

    @GraphState(name = CRAWL,
                next = CRAWL_WAIT,
                resume = ResumeBehavior.ERROR,
                description = """
                        Allocate a storage area for the crawled data,
                        then send a crawl request to the crawler and transition to CRAWL_WAIT.
                        """
    )
    public Message crawl(Message message) throws Exception {
        // Create processed data area

        var toCrawl = storageService.getStorage(message.crawlSpecId);

        var base = storageService.getStorageBase(FileStorageBaseType.SLOW);
        var dataArea = storageService.allocateTemporaryStorage(
                base,
                FileStorageType.CRAWL_DATA,
                "crawl-data",
                toCrawl.description());

        storageService.relateFileStorages(toCrawl.id(), dataArea.id());

        // Pre-send convert request
        var request = new CrawlRequest(message.crawlSpecId, dataArea.id());
        long id = mqCrawlerOutbox.sendAsync(CrawlRequest.class.getSimpleName(), gson.toJson(request));

        return message
                .withCrawlStorageId(dataArea.id())
                .withCrawlerMsgId(id);
    }

    @GraphState(
            name = CRAWL_WAIT,
            next = END,
            resume = ResumeBehavior.RETRY,
            description = """
                    Wait for the crawler to finish retreiving the data.
                    """
    )
    public Message crawlerWait(Message message) throws Exception {
        var rsp = processWatcher.waitResponse(mqCrawlerOutbox, ProcessService.ProcessId.CRAWLER, message.crawlerMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Crawler failed");

        return message;
    }


}
