package nu.marginalia.control.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.control.svc.ProcessOutboxFactory;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mqapi.converting.ConvertAction;
import nu.marginalia.mqapi.converting.ConvertRequest;
import nu.marginalia.mqapi.loading.LoadRequest;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
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
public class ReconvertAndLoadActor extends AbstractStateGraph {

    // STATES

    public static final String INITIAL = "INITIAL";
    public static final String RECONVERT = "RECONVERT";
    public static final String RECONVERT_WAIT = "RECONVERT-WAIT";
    public static final String LOAD = "LOAD";
    public static final String LOAD_WAIT = "LOAD-WAIT";
    public static final String SWAP_LEXICON = "SWAP-LEXICON";

    public static final String REPARTITION = "REPARTITION";
    public static final String REPARTITION_WAIT = "REPARTITION-WAIT";
    public static final String REINDEX = "REINDEX";
    public static final String REINDEX_WAIT = "REINDEX-WAIT";
    public static final String END = "END";
    private final ProcessService processService;
    private final MqOutbox mqConverterOutbox;
    private final MqOutbox mqLoaderOutbox;
    private final MqOutbox indexOutbox;
    private final FileStorageService storageService;
    private final Gson gson;
    private final Logger logger = LoggerFactory.getLogger(getClass());


    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId crawlStorageId = null;
        public FileStorageId processedStorageId = null;
        public long converterMsgId = 0L;
        public long loaderMsgId = 0L;
    };

    @Inject
    public ReconvertAndLoadActor(StateFactory stateFactory,
                                 ProcessService processService,
                                 ProcessOutboxFactory processOutboxFactory,
                                 FileStorageService storageService,
                                 IndexClient indexClient,
                                 Gson gson
                                   )
    {
        super(stateFactory);
        this.indexOutbox = indexClient.outbox();
        this.processService = processService;
        this.mqConverterOutbox = processOutboxFactory.createConverterOutbox();
        this.mqLoaderOutbox = processOutboxFactory.createLoaderOutbox();
        this.storageService = storageService;
        this.gson = gson;
    }

    @GraphState(name = INITIAL,
                next = RECONVERT,
                description = """
                    Validate the input and transition to RECONVERT
                    """)
    public Message init(FileStorageId crawlStorageId) throws Exception {
        if (null == crawlStorageId) {
            error("This Actor requires a FileStorageId to be passed in as a parameter to INITIAL");
        }

        var storage = storageService.getStorage(crawlStorageId);

        if (storage == null) error("Bad storage id");
        if (storage.type() != FileStorageType.CRAWL_DATA) error("Bad storage type " + storage.type());

        return new Message().withCrawlStorageId(crawlStorageId);
    }

    @GraphState(name = RECONVERT,
                next = RECONVERT_WAIT,
                resume = ResumeBehavior.ERROR,
                description = """
                        Allocate a storage area for the processed data,
                        then send a convert request to the converter and transition to RECONVERT_WAIT.
                        """
    )
    public Message reconvert(Message message) throws Exception {
        // Create processed data area

        var toProcess = storageService.getStorage(message.crawlStorageId);

        var base = storageService.getStorageBase(FileStorageBaseType.SLOW);
        var processedArea = storageService.allocateTemporaryStorage(base, FileStorageType.PROCESSED_DATA, "processed-data",
                "Processed Data; " + toProcess.description());

        storageService.relateFileStorages(toProcess.id(), processedArea.id());

        // Pre-send convert request
        var request = new ConvertRequest(ConvertAction.ConvertCrawlData,
                null,
                message.crawlStorageId,
                processedArea.id());
        long id = mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request));

        return message
                .withProcessedStorageId(processedArea.id())
                .withConverterMsgId(id);
    }

    @GraphState(
            name = RECONVERT_WAIT,
            next = LOAD,
            resume = ResumeBehavior.RETRY,
            description = """
                    Wait for the converter to finish processing the data.
                    """
    )
    public Message reconvertWait(Message message) throws Exception {
        var rsp = waitResponse(mqConverterOutbox, ProcessService.ProcessId.CONVERTER, message.converterMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Converter failed");

        return message;
    }


    @GraphState(
            name = LOAD,
            next = LOAD_WAIT,
            resume = ResumeBehavior.ERROR,
            description = """
                    Send a load request to the loader and transition to LOAD_WAIT.
                    """)
    public Message load(Message message) throws Exception {

        var request = new LoadRequest(message.processedStorageId);
        long id = mqLoaderOutbox.sendAsync(LoadRequest.class.getSimpleName(), gson.toJson(request));

        return message.withLoaderMsgId(id);

    }

    @GraphState(
            name = LOAD_WAIT,
            next = SWAP_LEXICON,
            resume = ResumeBehavior.RETRY,
            description = """
                    Wait for the loader to finish loading the data.
                    """
    )
    public void loadWait(Message message) throws Exception {
        var rsp = waitResponse(mqLoaderOutbox, ProcessService.ProcessId.LOADER, message.loaderMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Loader failed");
    }



    @GraphState(
            name = SWAP_LEXICON,
            next = REPARTITION,
            resume = ResumeBehavior.RETRY,
            description = """
                    Move the lexicon from the LEXICON_STAGING area to the LEXICON_LIVE area,
                    then instruct the index-service to reload the lexicon.
                    """
    )
    public void swapLexicon(Message message) throws Exception {
        var live = storageService.getStorageByType(FileStorageType.LEXICON_LIVE);

        var staging = storageService.getStorageByType(FileStorageType.LEXICON_STAGING);
        var fromSource = staging.asPath().resolve("dictionary.dat");
        var liveDest = live.asPath().resolve("dictionary.dat");

        // Swap in new lexicon
        logger.info("Moving " + fromSource + " to " + liveDest);
        Files.move(fromSource, liveDest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }


    @GraphState(
            name = REPARTITION,
            next = REPARTITION_WAIT,
            description = """
                    Instruct the index-service to repartition the index then transition to REPARTITION_WAIT.
                    """
    )
    public Long repartition() throws Exception {
        return indexOutbox.sendAsync(IndexMqEndpoints.INDEX_REPARTITION, "");
    }

    @GraphState(
            name = REPARTITION_WAIT,
            next = REINDEX,
            resume = ResumeBehavior.RETRY,
            description = """
                    Wait for the index-service to finish repartitioning the index.
                    """
    )
    public void repartitionReply(Long id) throws Exception {
        var rsp = indexOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

    @GraphState(
            name = REINDEX,
            next = REINDEX_WAIT,
            description = """
                    Instruct the index-service to reindex the data then transition to REINDEX_WAIT.
                    """
    )
    public Long reindex() throws Exception {
        return indexOutbox.sendAsync(IndexMqEndpoints.INDEX_REINDEX, "");
    }

    @GraphState(
            name = REINDEX_WAIT,
            next = END,
            resume = ResumeBehavior.RETRY,
            description = """
                    Wait for the index-service to finish reindexing the data.
                    """
    )
    public void reindexReply(Long id) throws Exception {
        var rsp = indexOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
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
