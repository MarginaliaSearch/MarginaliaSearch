package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.tasks.ExportTaskRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessSpawnerService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Singleton
public class ExportDomSampleDataActor extends RecordActorPrototype {
    private final FileStorageService storageService;
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox exportTasksOutbox;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final MqPersistence persistence;

    public record Export(long responseMsgId) implements ActorStep {}
    public record Run(long responseMsgId, FileStorageId destId, long msgId) implements ActorStep {
        public Run(long responseMsgId, FileStorageId destId) {
            this(responseMsgId, destId, -1);
        }
    }
    public record Fail(long responseMsgId, String message) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(long responseMsgId) -> {
                persistence.updateMessageState(responseMsgId, MqMessageState.ACK);

                var storage = storageService.allocateStorage(FileStorageType.EXPORT, "domain-sample-data-export", "Domain Sample Data " + LocalDateTime.now());

                if (storage == null) yield new Fail(responseMsgId, "Bad storage id");

                yield new Run(responseMsgId, storage.id());
            }
            case Run(long responseMsgId, FileStorageId destId, long msgId) when msgId < 0 -> {
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                long newMsgId = exportTasksOutbox.sendAsync(ExportTaskRequest.domSampleData(destId));
                yield new Run(responseMsgId, destId, newMsgId);
            }
            case Run(long responseMsgId, FileStorageId destId, long msgId) -> {
                var rsp = processWatcher.waitResponse(exportTasksOutbox, ProcessSpawnerService.ProcessId.EXPORT_TASKS, msgId);

                if (rsp.state() != MqMessageState.OK) {
                    storageService.flagFileForDeletion(destId);
                    yield new Fail(responseMsgId, "Exporter failed");
                }
                else {
                    storageService.setFileStorageState(destId, FileStorageState.UNSET);
                    persistence.updateMessageState(responseMsgId, MqMessageState.OK);
                    yield new End();
                }
            }
            case Fail(long responseMsgId, String message) -> {
                persistence.updateMessageState(responseMsgId, MqMessageState.ERR);
                yield new Error(message);
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Export domain sample data";
    }

    @Inject
    public ExportDomSampleDataActor(Gson gson,
                                    FileStorageService storageService,
                                    ProcessOutboxes processOutboxes,
                                    MqPersistence persistence,
                                    ActorProcessWatcher processWatcher)
    {
        super(gson);
        this.exportTasksOutbox = processOutboxes.getExportTasksOutbox();
        this.storageService = storageService;
        this.persistence = persistence;
        this.processWatcher = processWatcher;
    }

}
