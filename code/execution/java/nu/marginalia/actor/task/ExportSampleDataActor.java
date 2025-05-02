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
public class ExportSampleDataActor extends RecordActorPrototype {
    private final FileStorageService storageService;
    private final ActorProcessWatcher processWatcher;
    private final MqOutbox exportTasksOutbox;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Export(FileStorageId crawlId, int size, String ctFilter, String name) implements ActorStep {}
    public record Run(FileStorageId crawlId, FileStorageId destId, int size, String ctFilter, String name, long msgId) implements ActorStep {
        public Run(FileStorageId crawlId, FileStorageId destId, int size, String name, String ctFilter) {
            this(crawlId, destId, size, name, ctFilter,-1);
        }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Export(FileStorageId crawlId, int size, String ctFilter, String name) -> {
                var storage = storageService.allocateStorage(FileStorageType.EXPORT,
                        "crawl-sample-export",
                        "Crawl Data Sample " + name + "/" + size + " " + LocalDateTime.now()
                );

                if (storage == null) yield new Error("Bad storage id");
                yield new Run(crawlId, storage.id(), size, ctFilter, name);
            }
            case Run(FileStorageId crawlId, FileStorageId destId, int size, String ctFilter, String name, long msgId) when msgId < 0 -> {
                storageService.setFileStorageState(destId, FileStorageState.NEW);

                long newMsgId = exportTasksOutbox.sendAsync(ExportTaskRequest.sampleData(crawlId, destId, ctFilter, size, name));
                yield new Run(crawlId, destId, size, ctFilter, name, newMsgId);
            }
            case Run(_, FileStorageId destId, _, _, _, long msgId) -> {
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
        return "Export sample crawl data";
    }

    @Inject
    public ExportSampleDataActor(Gson gson,
                                 FileStorageService storageService,
                                 ProcessOutboxes processOutboxes,
                                 ActorProcessWatcher processWatcher)
    {
        super(gson);
        this.storageService = storageService;
        this.processWatcher = processWatcher;
        this.exportTasksOutbox = processOutboxes.getExportTasksOutbox();
    }

}
