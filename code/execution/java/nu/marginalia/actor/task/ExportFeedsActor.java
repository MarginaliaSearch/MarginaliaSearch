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
public class ExportFeedsActor extends RecordActorPrototype {
    private final FileStorageService storageService;
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox exportTasksOutbox;
    private final MqPersistence persistence;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Export(long responseMsgId, FileStorageId crawlId) implements ActorStep {}
    public record Run(long responseMsgId, FileStorageId crawlId, FileStorageId destId, long msgId) implements ActorStep {
        public Run(long responseMsgId, FileStorageId crawlId, FileStorageId destId) {
            this(responseMsgId, crawlId, destId, -1);
        }
    }
    public record Fail(long responseMsgId, String message) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(long responseMsgId, FileStorageId crawlId) -> {
                persistence.updateMessageState(responseMsgId, MqMessageState.ACK);

                var storage = storageService.allocateStorage(FileStorageType.EXPORT, "feed-export", "Feeds " + LocalDateTime.now());

                if (storage == null) yield new Fail(responseMsgId, "Bad storage id");
                yield new Run(responseMsgId, crawlId, storage.id());
            }
            case Run(long responseMsgId, FileStorageId crawlId, FileStorageId destId, long msgId) when msgId < 0 -> {
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                long newMsgId = exportTasksOutbox.sendAsync(ExportTaskRequest.feeds(crawlId, destId));
                yield new Run(responseMsgId, crawlId, destId, newMsgId);
            }
            case Run(long responseMsgId, _, FileStorageId destId, long msgId) -> {
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
        return "Export RSS/Atom feeds from crawl data";
    }

    @Inject
    public ExportFeedsActor(Gson gson,
                            FileStorageService storageService,
                            ActorProcessWatcher processWatcher,
                            ProcessOutboxes outboxes, MqPersistence persistence)
    {
        super(gson);
        this.storageService = storageService;
        this.processWatcher = processWatcher;
        this.exportTasksOutbox = outboxes.getExportTasksOutbox();
        this.persistence = persistence;
    }

}
