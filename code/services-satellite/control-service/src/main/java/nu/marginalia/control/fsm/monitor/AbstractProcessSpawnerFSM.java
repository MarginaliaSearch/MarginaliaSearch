package nu.marginalia.control.fsm.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Singleton
public class AbstractProcessSpawnerFSM extends AbstractStateGraph {

    private final MqPersistence persistence;
    private final ProcessService processService;
    public static final String INITIAL = "INITIAL";
    public static final String MONITOR = "MONITOR";
    public static final String RUN = "RUN";
    public static final String END = "END";

    public static final int MAX_ATTEMPTS = 3;
    private final String inboxName;
    private final ProcessService.ProcessId processId;

    @Inject
    public AbstractProcessSpawnerFSM(StateFactory stateFactory,
                                     MqPersistence persistence,
                                     ProcessService processService,
                                     String inboxName,
                                     ProcessService.ProcessId processId) {
        super(stateFactory);
        this.persistence = persistence;
        this.processService = processService;
        this.inboxName = inboxName;
        this.processId = processId;
    }

    @GraphState(name = INITIAL, next = MONITOR)
    public void init() {

    }

    @GraphState(name = MONITOR, resume = ResumeBehavior.RETRY)
    public void monitor() throws SQLException, InterruptedException {

        for (;;) {
            var messages = persistence.eavesdrop(inboxName, 1);

            if (messages.isEmpty() && !processService.isRunning(processId)) {
                TimeUnit.SECONDS.sleep(5);
            } else {
                transition(RUN, 0);
            }
        }
    }

    @GraphState(name = RUN, resume = ResumeBehavior.RESTART)
    public void run(Integer attempts) throws Exception {
        try {
            processService.trigger(processId);
        }
        catch (Exception e) {
            if (attempts < MAX_ATTEMPTS) {
                transition(RUN, attempts + 1);
            }
            else throw e;
        }

        transition(MONITOR);
    }

}
