package nu.marginalia.control.fsm.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.control.svc.ProcessOutboxFactory;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.mqapi.converting.ConvertRequest;
import nu.marginalia.mqapi.loading.LoadRequest;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import nu.marginalia.search.client.SearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class ReconvertAndLoadFSM extends AbstractStateGraph {

    // STATES

    public static final String INITIAL = "INITIAL";
    public static final String RECONVERT = "RECONVERT";
    public static final String RECONVERT_WAIT = "RECONVERT-WAIT";
    public static final String LOAD = "LOAD";
    public static final String LOAD_WAIT = "LOAD-WAIT";
    public static final String SWAP_LEXICON = "SWAP-LEXICON";
    public static final String END = "END";
    private final ProcessService processService;
    private final MqOutbox mqConverterOutbox;
    private final MqOutbox mqLoaderOutbox;
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
    public ReconvertAndLoadFSM(StateFactory stateFactory,
                               ProcessService processService,
                               ProcessOutboxFactory processOutboxFactory,
                               FileStorageService storageService,
                               Gson gson
                                   )
    {
        super(stateFactory);
        this.processService = processService;
        this.mqConverterOutbox = processOutboxFactory.createConverterOutbox();
        this.mqLoaderOutbox = processOutboxFactory.createLoaderOutbox();
        this.storageService = storageService;
        this.gson = gson;
    }

    @GraphState(name = INITIAL, next = RECONVERT)
    public Message init(FileStorageId crawlStorageId) throws Exception {
        var storage = storageService.getStorage(crawlStorageId);

        if (storage == null) error("Bad storage id");
        if (storage.type() != FileStorageType.CRAWL_DATA) error("Bad storage type " + storage.type());

        return new Message().withCrawlStorageId(crawlStorageId);
    }

    @GraphState(name = RECONVERT, next = RECONVERT_WAIT, resume = ResumeBehavior.ERROR)
    public Message reconvert(Message message) throws Exception {
        // Create processed data area

        var toProcess = storageService.getStorage(message.crawlStorageId);

        var base = storageService.getStorageBase(FileStorageBaseType.SLOW);
        var processedArea = storageService.allocateTemporaryStorage(base, FileStorageType.PROCESSED_DATA, "processed-data",
                "Processed Data; " + toProcess.description());

        // Pre-send convert request
        var request = new ConvertRequest(message.crawlStorageId, processedArea.id());
        long id = mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request));

        return message
                .withProcessedStorageId(processedArea.id())
                .withConverterMsgId(id);
    }
    @GraphState(name = RECONVERT_WAIT, next = LOAD, resume = ResumeBehavior.RETRY)
    public Message reconvertWait(Message message) throws Exception {
        var rsp = waitResponse(mqConverterOutbox, ProcessService.ProcessId.CONVERTER, message.converterMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Converter failed");

        return message;
    }


    @GraphState(name = LOAD, next = LOAD_WAIT, resume = ResumeBehavior.ERROR)
    public Message load(Message message) throws Exception {

        var request = new LoadRequest(message.processedStorageId);
        long id = mqLoaderOutbox.sendAsync(LoadRequest.class.getSimpleName(), gson.toJson(request));

        return message.withLoaderMsgId(id);

    }

    @GraphState(name = LOAD_WAIT, next = SWAP_LEXICON, resume = ResumeBehavior.RETRY)
    public void loadWait(Message message) throws Exception {
        var rsp = waitResponse(mqLoaderOutbox, ProcessService.ProcessId.LOADER, message.loaderMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Loader failed");
    }



    @GraphState(name = SWAP_LEXICON, next = END, resume = ResumeBehavior.RETRY)
    public void swapLexicon(Message message) throws Exception {
        var live = storageService.getStorageByType(FileStorageType.LEXICON_LIVE);

        var staging = storageService.getStorageByType(FileStorageType.LEXICON_STAGING);
        var fromSource = staging.asPath().resolve("dictionary.dat");
        var liveDest = live.asPath().resolve("dictionary.dat");

        // Backup live lexicon
        var backupBase = storageService.getStorageBase(FileStorageBaseType.BACKUP);
        var backup = storageService.allocateTemporaryStorage(backupBase, FileStorageType.BACKUP,
                "lexicon", "Lexicon Backup; " + LocalDateTime.now());

        Path backupDest = backup.asPath().resolve("dictionary.dat");

        logger.info("Moving " + liveDest + " to " + backupDest);
        Files.move(liveDest, backupDest);

        // Swap in new lexicon
        logger.info("Moving " + fromSource + " to " + liveDest);
        Files.move(fromSource, liveDest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
                if (!waitForProcess(processId, TimeUnit.SECONDS, 30)) {
                    error("Process " + processId + " died and did not re-launch");
                }
            }
        }
    }

    public boolean waitForProcess(ProcessService.ProcessId processId, TimeUnit unit, int duration) throws InterruptedException {

        // Wait for process to start
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (System.currentTimeMillis() < deadline) {
            if (processService.isRunning(processId))
                return true;

            TimeUnit.SECONDS.sleep(1);
        }

        return false;
    }

}
