package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.tasks.ExportTaskRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Singleton
public class ExportAtagsActor extends RecordActorPrototype {
    private final FileStorageService storageService;
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox exportTasksOutbox;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Export(FileStorageId crawlId) implements ActorStep {}
    public record Run(FileStorageId crawlId, FileStorageId destId, long msgId) implements ActorStep {
        public Run(FileStorageId crawlId, FileStorageId destId) {
            this(crawlId, destId, -1);
        }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(FileStorageId crawlId) -> {
                var storage = storageService.allocateStorage(FileStorageType.EXPORT, "atags-export", "Atags " + LocalDateTime.now());

                if (storage == null) yield new Error("Bad storage id");
                yield new Run(crawlId, storage.id());
            }
            case Run(FileStorageId crawlId, FileStorageId destId, long msgId) when msgId < 0 -> {
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                long newMsgId = exportTasksOutbox.sendAsync(ExportTaskRequest.atags(crawlId, destId));
                yield new Run(crawlId, destId, newMsgId);
            }
            case Run(_, FileStorageId destId, long msgId) -> {
                var rsp = processWatcher.waitResponse(exportTasksOutbox, ProcessService.ProcessId.EXPORT_TASKS, msgId);

                if (rsp.state() != MqMessageState.OK) {
                    storageService.flagFileForDeletion(destId);
                    yield new Error("Exporter failed");
                }
                else {
                    storageService.setFileStorageState(destId, FileStorageState.UNSET);
                    yield new End();
                }
            }

            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Export anchor tags from crawl data";
    }

    @Inject
    public ExportAtagsActor(Gson gson,
                            FileStorageService storageService,
                            ProcessOutboxes processOutboxes,
                            ActorProcessWatcher processWatcher)
    {
        super(gson);
        this.exportTasksOutbox = processOutboxes.getExportTasksOutbox();
        this.storageService = storageService;
        this.processWatcher = processWatcher;
    }

}
