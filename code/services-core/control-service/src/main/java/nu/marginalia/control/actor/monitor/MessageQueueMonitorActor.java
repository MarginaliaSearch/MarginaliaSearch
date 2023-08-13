package nu.marginalia.control.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;

import java.util.concurrent.TimeUnit;

@Singleton
public class MessageQueueMonitorActor extends AbstractStateGraph {

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
    public MessageQueueMonitorActor(StateFactory stateFactory,
                                    MqPersistence persistence) {
        super(stateFactory);
        this.persistence = persistence;
    }

    @GraphState(name = INITIAL, next = MONITOR)
    public void init() {
    }

    @GraphState(name = MONITOR, next = MONITOR, resume = ResumeBehavior.RETRY,
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
