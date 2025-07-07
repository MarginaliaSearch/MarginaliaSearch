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
import nu.marginalia.process.ProcessSpawnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TriggerAdjacencyCalculationActor extends RecordActorPrototype {

    private final ActorProcessWatcher processWatcher;
    private final MqOutbox exportTasksOutbox;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public record Run(long msgId) implements ActorStep {
        public Run() {
            this(-1);
        }
    }

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Run(long msgId) when msgId < 0 -> {
                long newMsgId = exportTasksOutbox.sendAsync(ExportTaskRequest.adjacencies());
                yield new Run(newMsgId);
            }
            case Run(long msgId) -> {
                var rsp = processWatcher.waitResponse(exportTasksOutbox, ProcessSpawnerService.ProcessId.EXPORT_TASKS, msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("Exporter failed");
                }
                else {
                    yield new End();
                }
            }

            default -> new Error();
        };
    }


    @Inject
    public TriggerAdjacencyCalculationActor(Gson gson,
                                            ProcessOutboxes processOutboxes,
                                            ActorProcessWatcher processWatcher) {
        super(gson);

        this.exportTasksOutbox = processOutboxes.getExportTasksOutbox();
        this.processWatcher = processWatcher;

    }

    @Override
    public String describe() {
        return "Calculate website similarities";
    }

}
