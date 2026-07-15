package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqapi.ndp.NdpRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessSpawnerService;

@Singleton
public class NdpActor extends RecordActorPrototype {

    private final MqOutbox mqNdpOutbox;
    private final ActorProcessWatcher processWatcher;

    public record Initial(int goal) implements ActorStep {}

    /** The action step */
    @Resume(behavior = ActorResumeBehavior.RETRY)
    public record Run(long msgId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial(int goal) -> {
                if (goal <= 0) {
                    yield new Error("Invalid domain discovery goal: " + goal);
                }

                long id = mqNdpOutbox.sendAsync(new NdpRequest(goal));

                yield new Run(id);
            }
            case Run(long msgId) -> {
                var rsp = processWatcher.waitResponse(
                        mqNdpOutbox,
                        ProcessSpawnerService.ProcessId.NDP,
                        msgId);

                if (rsp.state() != MqMessageState.OK) {
                    yield new Error("New domain process failed");
                }

                yield new End();
            }
            default -> new End();
        };
    }

    @Override
    public String describe() {
        return "Runs the new domain process, discovering and assigning new domains for crawling until the total domain count reaches the given goal";
    }

    @Inject
    public NdpActor(ActorProcessWatcher processWatcher,
                    ProcessOutboxes processOutboxes,
                    Gson gson)
    {
        super(gson);

        this.processWatcher = processWatcher;
        this.mqNdpOutbox = processOutboxes.getNdpOutbox();
    }

}
