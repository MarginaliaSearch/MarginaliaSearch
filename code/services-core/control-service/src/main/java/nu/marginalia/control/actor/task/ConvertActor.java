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
import nu.marginalia.mqapi.converting.ConvertAction;
import nu.marginalia.mqapi.converting.ConvertRequest;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class ConvertActor extends AbstractStateGraph {

    // STATES

    public static final String CONVERT = "CONVERT";
    public static final String CONVERT_ENCYCLOPEDIA = "CONVERT_ENCYCLOPEDIA";
    public static final String CONVERT_STACKEXCHANGE = "CONVERT_STACKEXCHANGE";
    public static final String CONVERT_WAIT = "CONVERT-WAIT";

    public static final String END = "END";
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox mqConverterOutbox;
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
    public ConvertActor(StateFactory stateFactory,
                        ActorProcessWatcher processWatcher,
                        ProcessOutboxes processOutboxes,
                        FileStorageService storageService,
                        Gson gson
                                   )
    {
        super(stateFactory);
        this.processWatcher = processWatcher;
        this.mqConverterOutbox = processOutboxes.getConverterOutbox();
        this.storageService = storageService;
        this.gson = gson;
    }

    @GraphState(name = CONVERT,
                next = CONVERT_WAIT,
                resume = ResumeBehavior.ERROR,
                description = """
                        Allocate a storage area for the processed data,
                        then send a convert request to the converter and transition to RECONVERT_WAIT.
                        """
    )
    public Long convert(FileStorageId sourceStorageId) throws Exception {
        // Create processed data area

        var toProcess = storageService.getStorage(sourceStorageId);
        var base = storageService.getStorageBase(FileStorageBaseType.SLOW);
        var processedArea = storageService.allocateTemporaryStorage(base,
                FileStorageType.PROCESSED_DATA, "processed-data",
                "Processed Data; " + toProcess.description());

        storageService.relateFileStorages(toProcess.id(), processedArea.id());

        // Pre-send convert request
        var request = new ConvertRequest(ConvertAction.ConvertCrawlData,
                null,
                sourceStorageId,
                processedArea.id());

        return mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request));
    }

    @GraphState(name = CONVERT_ENCYCLOPEDIA,
            next = CONVERT_WAIT,
            resume = ResumeBehavior.ERROR,
            description = """
                        Allocate a storage area for the processed data,
                        then send a convert request to the converter and transition to RECONVERT_WAIT.
                        """
    )
    public Long convertEncyclopedia(String source) throws Exception {
        // Create processed data area

        Path sourcePath = Path.of(source);
        if (!Files.exists(sourcePath))
            error("Source path does not exist: " + sourcePath);

        String fileName = sourcePath.toFile().getName();

        var base = storageService.getStorageBase(FileStorageBaseType.SLOW);
        var processedArea = storageService.allocateTemporaryStorage(base,
                FileStorageType.PROCESSED_DATA, "processed-data",
                "Processed Encylopedia Data; " + fileName);

        // Pre-send convert request
        var request = new ConvertRequest(ConvertAction.SideloadEncyclopedia,
                sourcePath.toString(),
                null,
                processedArea.id());

        return mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request));
    }

    @GraphState(name = CONVERT_STACKEXCHANGE,
            next = CONVERT_WAIT,
            resume = ResumeBehavior.ERROR,
            description = """
                        Allocate a storage area for the processed data,
                        then send a convert request to the converter and transition to RECONVERT_WAIT.
                        """
    )
    public Long convertStackexchange(String source) throws Exception {
        // Create processed data area

        Path sourcePath = Path.of(source);
        if (!Files.exists(sourcePath))
            error("Source path does not exist: " + sourcePath);

        String fileName = sourcePath.toFile().getName();

        var base = storageService.getStorageBase(FileStorageBaseType.SLOW);
        var processedArea = storageService.allocateTemporaryStorage(base,
                FileStorageType.PROCESSED_DATA, "processed-data",
                "Processed Stackexchange Data; " + fileName);

        // Pre-send convert request
        var request = new ConvertRequest(ConvertAction.SideloadStackexchange,
                sourcePath.toString(),
                null,
                processedArea.id());

        return mqConverterOutbox.sendAsync(ConvertRequest.class.getSimpleName(), gson.toJson(request));
    }

    @GraphState(
            name = CONVERT_WAIT,
            next = END,
            resume = ResumeBehavior.RETRY,
            description = """
                    Wait for the converter to finish processing the data.
                    """
    )
    public void convertWait(Long msgId) throws Exception {
        var rsp = processWatcher.waitResponse(mqConverterOutbox, ProcessService.ProcessId.CONVERTER, msgId);

        if (rsp.state() != MqMessageState.OK)
            error("Converter failed");
    }


}
