package nu.marginalia.control.process;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.control.svc.ProcessOutboxFactory;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.converting.mqapi.ConvertRequest;
import nu.marginalia.converting.mqapi.LoadRequest;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import nu.marginalia.search.client.SearchClient;
import nu.marginalia.search.client.SearchMqEndpoints;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class ReconvertAndLoadProcess extends AbstractStateGraph {

    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String RECONVERT = "RECONVERT";
    private static final String RECONVERT_WAIT = "RECONVERT_WAIT";
    private static final String LOAD = "LOAD";
    private static final String LOAD_WAIT = "LOAD_WAIT";
    private static final String MOVE_INDEX_FILES = "MOVE_INDEX_FILES";
    private static final String RELOAD_LEXICON = "RELOAD_LEXICON";
    private static final String RELOAD_LEXICON_WAIT = "RELOAD_LEXICON_WAIT";
    private static final String FLUSH_CACHES = "FLUSH_CACHES";
    private static final String END = "END";
    private final ProcessService processService;
    private final MqOutbox mqIndexOutbox;
    private final MqOutbox mqSearchOutbox;
    private final MqOutbox mqConverterOutbox;
    private final MqOutbox mqLoaderOutbox;
    private final FileStorageService storageService;
    private final Gson gson;


    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId crawlStorageId = null;
        public FileStorageId processedStorageId = null;
        public long converterMsgId = 0L;
        public long loaderMsgId = 0L;
    };

    @Inject
    public ReconvertAndLoadProcess(StateFactory stateFactory,
                                   ProcessService processService,
                                   IndexClient indexClient,
                                   ProcessOutboxFactory processOutboxFactory,
                                   SearchClient searchClient,
                                   FileStorageService storageService,
                                   Gson gson
                                   )
    {
        super(stateFactory);
        this.processService = processService;
        this.mqIndexOutbox = indexClient.outbox();
        this.mqSearchOutbox = searchClient.outbox();
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
        var base = storageService.getStorageBase(FileStorageBaseType.SLOW);
        var processedArea = storageService.allocateTemporaryStorage(base, FileStorageType.PROCESSED_DATA, "processed-data", "Processed Data");

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

    @GraphState(name = LOAD_WAIT, next = END, resume = ResumeBehavior.RETRY)
    public void loadWait(Message message) throws Exception {
        var rsp = waitResponse(mqLoaderOutbox, ProcessService.ProcessId.LOADER, message.loaderMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Loader failed");
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

//    @GraphState(name = MOVE_INDEX_FILES, next = RELOAD_LEXICON, resume = ResumeBehavior.ERROR)
//    public void moveIndexFiles(String crawlJob) throws Exception {
//        Path indexData = Path.of("/vol/index.dat");
//        Path indexDest = Path.of("/vol/iw/0/page-index.dat");
//
//        if (!Files.exists(indexData))
//            error("Index data not found");
//
//        Files.move(indexData, indexDest, StandardCopyOption.REPLACE_EXISTING);
//    }
//
//    @GraphState(name = RELOAD_LEXICON, next = RELOAD_LEXICON_WAIT, resume = ResumeBehavior.ERROR)
//    public long reloadLexicon() throws Exception {
//        return mqIndexOutbox.sendAsync(IndexMqEndpoints.INDEX_RELOAD_LEXICON, "");
//    }
//
//    @GraphState(name = RELOAD_LEXICON_WAIT, next = FLUSH_CACHES, resume = ResumeBehavior.RETRY)
//    public void reloadLexiconWait(long id) throws Exception {
//        var rsp = mqIndexOutbox.waitResponse(id);
//
//        if (rsp.state() != MqMessageState.OK) {
//            error("RELOAD_LEXICON failed");
//        }
//    }
//
//    @GraphState(name = FLUSH_CACHES, next = END, resume = ResumeBehavior.RETRY)
//    public void flushCaches() throws Exception {
//        var rsp = mqSearchOutbox.send(SearchMqEndpoints.FLUSH_CACHES, "");
//
//        if (rsp.state() != MqMessageState.OK) {
//            error("FLUSH_CACHES failed");
//        }
//    }
}
