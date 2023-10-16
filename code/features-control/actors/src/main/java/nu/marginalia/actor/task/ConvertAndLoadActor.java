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
import nu.marginalia.control.process.ProcessOutboxes;
import nu.marginalia.control.process.ProcessService;
import nu.marginalia.svc.BackupService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.client.IndexMqEndpoints;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.converting.ConvertAction;
import nu.marginalia.mqapi.converting.ConvertRequest;
import nu.marginalia.mqapi.index.CreateIndexRequest;
import nu.marginalia.mqapi.index.IndexName;
import nu.marginalia.mqapi.loading.LoadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Singleton
public class ConvertAndLoadActor extends AbstractActorPrototype {

    // STATES

    public static final String INITIAL = "INITIAL";
    public static final String RECONVERT = "RECONVERT";
    public static final String RECONVERT_WAIT = "RECONVERT-WAIT";
    public static final String LOAD = "LOAD";
    public static final String BACKUP = "BACKUP";
    public static final String REPARTITION = "REPARTITION";
    public static final String REINDEX_FWD = "REINDEX_FWD";
    public static final String REINDEX_FULL = "REINDEX_FULL";
    public static final String REINDEX_PRIO = "REINDEX_PRIO";
    public static final String SWITCH_OVER = "SWITCH-OVER";

    public static final String END = "END";
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox mqConverterOutbox;
    private final MqOutbox mqLoaderOutbox;
    private final MqOutbox mqIndexConstructorOutbox;
    private final MqOutbox indexOutbox;
    private final FileStorageService storageService;
    private final BackupService backupService;
    private final Gson gson;
    private final Logger logger = LoggerFactory.getLogger(getClass());


    @AllArgsConstructor @With @NoArgsConstructor
    public static class Message {
        public FileStorageId crawlStorageId = null;
        public List<FileStorageId> processedStorageId = null;
        public long converterMsgId = 0L;
        public long loaderMsgId = 0L;
    };

    @Override
    public String describe() {
        return "Process a set of crawl data and then load it into the database.";
    }

    @Inject
    public ConvertAndLoadActor(ActorStateFactory stateFactory,
                               ActorProcessWatcher processWatcher,
                               ProcessOutboxes processOutboxes,
                               FileStorageService storageService,
                               IndexClient indexClient,
                               BackupService backupService,
                               Gson gson
                                   )
    {
        super(stateFactory);
        this.processWatcher = processWatcher;
        this.indexOutbox = indexClient.outbox();
        this.mqConverterOutbox = processOutboxes.getConverterOutbox();
        this.mqLoaderOutbox = processOutboxes.getLoaderOutbox();
        this.mqIndexConstructorOutbox = processOutboxes.getIndexConstructorOutbox();
        this.storageService = storageService;
        this.backupService = backupService;
        this.gson = gson;
    }

    @ActorState(name = INITIAL,
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

    @ActorState(name = RECONVERT,
                next = RECONVERT_WAIT,
                resume = ActorResumeBehavior.ERROR,
                description = """
                        Allocate a storage area for the processed data,
                        then send a convert request to the converter and transition to RECONVERT_WAIT.
                        """
    )
    public Message reconvert(Message message) throws Exception {
        // Create processed data area

        var toProcess = storageService.getStorage(message.crawlStorageId);

        var base = storageService.getStorageBase(FileStorageBaseType.STORAGE);
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
                .withProcessedStorageId(List.of(processedArea.id()))
                .withConverterMsgId(id);
    }

    @ActorState(
            name = RECONVERT_WAIT,
            next = LOAD,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Wait for the converter to finish processing the data.
                    """
    )
    public Message reconvertWait(Message message) throws Exception {
        var rsp = processWatcher.waitResponse(mqConverterOutbox, ProcessService.ProcessId.CONVERTER, message.converterMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Converter failed");

        return message;
    }


    @ActorState(
            name = LOAD,
            next = BACKUP,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Instruct the loader to process the data
                    """)
    public Message load(Message message) throws Exception {
        if (message.loaderMsgId <= 0) {
            var request = new LoadRequest(message.processedStorageId);
            long id = mqLoaderOutbox.sendAsync(LoadRequest.class.getSimpleName(), gson.toJson(request));

            transition(LOAD, message.withLoaderMsgId(id));
        }
        var rsp = processWatcher.waitResponse(mqLoaderOutbox, ProcessService.ProcessId.LOADER, message.loaderMsgId);

        if (rsp.state() != MqMessageState.OK)
            error("Loader failed");

        return message;
    }

    @ActorState(
            name = BACKUP,
            next = REPARTITION,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Create a backup snapshot of the new data
                    """)
    public void createBackup(Message message) throws SQLException, IOException {
        backupService.createBackupFromStaging(message.processedStorageId);
    }

    @ActorState(
            name = REPARTITION,
            next = REINDEX_FWD,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Instruct the index-service to repartition.
                    """
    )
    public void repartition(Long id) throws Exception {
        if (id == null) {
            transition(REPARTITION, indexOutbox.sendAsync(IndexMqEndpoints.INDEX_REPARTITION, ""));
        }

        var rsp = indexOutbox.waitResponse(id);
        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

    @ActorState(
            name = REINDEX_FWD,
            next = REINDEX_FULL,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Reconstruct the fwd index
                    """
    )
    public void reindexFwd(Long id) throws Exception {
        if (id == null) {
            var request = new CreateIndexRequest(IndexName.FORWARD);
            transition(REINDEX_FWD, mqIndexConstructorOutbox.sendAsync(CreateIndexRequest.class.getSimpleName(), gson.toJson(request)));
        }

        var rsp = mqIndexConstructorOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

    @ActorState(
            name = REINDEX_FULL,
            next = REINDEX_PRIO,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Reconstruct the reverse full index
                    """
    )
    public void reindexFull(Long id) throws Exception {
        if (id == null) {
            var request = new CreateIndexRequest(IndexName.REVERSE_FULL);
            transition(REINDEX_FULL, mqIndexConstructorOutbox.sendAsync(CreateIndexRequest.class.getSimpleName(), gson.toJson(request)));
        }

        var rsp = mqIndexConstructorOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

    @ActorState(
            name = REINDEX_PRIO,
            next = SWITCH_OVER,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Reconstruct the reverse prio index
                    """
    )
    public void reindexPrio(Long id) throws Exception {
        if (id == null) {
            var request = new CreateIndexRequest(IndexName.REVERSE_PRIO);
            transition(REINDEX_PRIO, mqIndexConstructorOutbox.sendAsync(CreateIndexRequest.class.getSimpleName(), gson.toJson(request)));
        }

        var rsp = mqIndexConstructorOutbox.waitResponse(id);

        if (rsp.state() != MqMessageState.OK) {
            error("Repartition failed");
        }
    }

    @ActorState(
            name = SWITCH_OVER,
            next = END,
            resume = ActorResumeBehavior.RETRY,
            description = """
                    Move the new lexicon into place, instruct the index service to
                    switch to the new linkdb, and the new index.
                    """
    )
    public void switchOver(Long id) throws Exception {
        // Notify index to switch over
        indexOutbox.sendNotice(IndexMqEndpoints.SWITCH_INDEX, ":^D");
        indexOutbox.sendNotice(IndexMqEndpoints.SWITCH_LINKDB, ":-)");
    }

}
