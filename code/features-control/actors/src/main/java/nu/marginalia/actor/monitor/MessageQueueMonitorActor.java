package nu.marginalia.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.ActorStateFactory;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.mq.persistence.MqPersistence;

import java.util.concurrent.TimeUnit;

@Singleton
public class MessageQueueMonitorActor extends AbstractActorPrototype {

    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String MONITOR = "MONITOR";
    private static final String END = "END";
    private final MqPersistence persistence;

    @Override
    public String describe() {
        return "Periodically run maintenance tasks on the message queue";
    }

    @Inject
    public MessageQueueMonitorActor(ActorStateFactory stateFactory,
                                    MqPersistence persistence) {
        super(stateFactory);
        this.persistence = persistence;
    }

    @ActorState(name = INITIAL, next = MONITOR)
    public void init() {
    }

    @ActorState(name = MONITOR, next = MONITOR, resume = ActorResumeBehavior.RETRY,
            description = """
                    Periodically clean up the message queue.
                    """)
    public void monitor() throws Exception {

        for (;;) {
            persistence.reapDeadMessages();
            persistence.cleanOldMessages();
            TimeUnit.SECONDS.sleep(60);
        }
    }

}
