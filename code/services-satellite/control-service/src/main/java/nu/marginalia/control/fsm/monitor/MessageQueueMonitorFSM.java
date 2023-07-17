package nu.marginalia.control.fsm.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;

import java.util.concurrent.TimeUnit;

@Singleton
public class MessageQueueMonitorFSM extends AbstractStateGraph {

    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String MONITOR = "MONITOR";
    private static final String END = "END";
    private final MqPersistence persistence;


    @Inject
    public MessageQueueMonitorFSM(StateFactory stateFactory,
                                  MqPersistence persistence) {
        super(stateFactory);
        this.persistence = persistence;
    }

    @GraphState(name = INITIAL, next = MONITOR)
    public void init() {
    }

    @GraphState(name = MONITOR, resume = ResumeBehavior.RETRY)
    public void monitor() throws Exception {

        for (;;) {
            persistence.reapDeadMessages();
            persistence.cleanOldMessages();
            TimeUnit.SECONDS.sleep(60);
        }
    }

}
