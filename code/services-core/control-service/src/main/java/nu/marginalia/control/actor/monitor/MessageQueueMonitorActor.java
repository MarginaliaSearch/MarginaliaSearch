package nu.marginalia.control.actor.monitor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.mq.persistence.MqPersistence;

import java.util.concurrent.TimeUnit;

@Singleton
public class MessageQueueMonitorActor extends RecordActorPrototype {

    private final MqPersistence persistence;

    public record Initial() implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Monitor() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial i -> new Monitor();
            case Monitor m -> {
                for (;;) {
                    persistence.reapDeadMessages();
                    persistence.cleanOldMessages();
                    TimeUnit.SECONDS.sleep(60);
                }
            }
            default -> new Error();
        };
    }

    @Inject
    public MessageQueueMonitorActor(Gson gson,
                                    MqPersistence persistence) {
        super(gson);
        this.persistence = persistence;
    }

    @Override
    public String describe() {
        return "Periodically run maintenance tasks on the message queue";
    }

}
