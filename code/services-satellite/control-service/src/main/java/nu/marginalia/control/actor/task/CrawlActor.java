package nu.marginalia.control.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.control.svc.ProcessOutboxFactory;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.converting.ConvertRequest;
import nu.marginalia.mqapi.crawling.CrawlRequest;
import nu.marginalia.mqapi.loading.LoadRequest;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class CrawlActor extends AbstractStateGraph {

    // STATES

    public static final String INITIAL = "INITIAL";
    public static final String CRAWL = "CRAWL";
    public static final String CRAWL_WAIT = "CRAWL-WAIT";
    public static final String END = "END";
    private final ProcessService processService;
    private final MqOutbox mqCrawlerOutbox;
    private final FileStorageService storageService;
    private final Gson gson;
    private final Logger logger = LoggerFactory.getLogger(getClass());


    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId crawlSpecId = null;
        public FileStorageId crawlStorageId = null;
        public long crawlerMsgId = 0L;
    };

    @Inject
    public CrawlActor(StateFactory stateFactory,
                      ProcessService processService,
                      ProcessOutboxFactory processOutboxFactory,
                      FileStorageService storageService,
                      Gson gson
                                   )
    {
        super(stateFactory);
        this.processService = processService;
        this.mqCrawlerOutbox = processOutboxFactory.createCrawlerOutbox();
        this.storageService = storageService;
        this.gson = gson;
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
        var rsp = waitResponse(mqCrawlerOutbox, ProcessService.ProcessId.CRAWLER, message.crawlerMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Crawler failed");

        return message;
    }


    public MqMessage waitResponse(MqOutbox outbox, ProcessService.ProcessId processId, long id) throws Exception {
        if (!waitForProcess(processId, TimeUnit.SECONDS, 30)) {
            error("Process " + processId + " did not launch");
        }
        for (;;) {
            try {
                return outbox.waitResponse(id, 1, TimeUnit.SECONDS);
            }
            catch (TimeoutException ex) {
                // Maybe the process died, wait a moment for it to restart
                if (!waitForProcess(processId, TimeUnit.SECONDS, 30)) {
                    error("Process " + processId + " died and did not re-launch");
                }
            }
        }
    }

    public boolean waitForProcess(ProcessService.ProcessId processId, TimeUnit unit, int duration) throws InterruptedException {

        // Wait for process to start
        long deadline = System.currentTimeMillis() + unit.toMillis(duration);
        while (System.currentTimeMillis() < deadline) {
            if (processService.isRunning(processId))
                return true;

            TimeUnit.SECONDS.sleep(1);
        }

        return false;
    }

}
