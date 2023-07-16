package nu.marginalia.control.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.converting.mqapi.ConverterInboxNames;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Singleton
public class LoaderMonitorProcess extends AbstractStateGraph {

    private final MqPersistence persistence;
    private final ProcessService processService;
    public static final String INITIAL = "INITIAL";
    public static final String CHECK = "CHECK";
    public static final String RUN = "RUN";
    public static final String END = "END";

    public static final int MAX_ATTEMPTS = 1;
    public static final String inboxName = ConverterInboxNames.LOADER_INBOX;
    public static final ProcessService.ProcessId processId = ProcessService.ProcessId.LOADER;

    @Inject
    public LoaderMonitorProcess(StateFactory stateFactory,
                                MqPersistence persistence,
                                ProcessService processService) {
        super(stateFactory);
        this.persistence = persistence;
        this.processService = processService;
    }

    @GraphState(name = INITIAL, next = CHECK)
    public void init() {

    }

    @GraphState(name = CHECK, resume = ResumeBehavior.RETRY)
    public void check() throws SQLException, InterruptedException {

        for (;;) {
            var messages = persistence.eavesdrop(inboxName, 1);

            if (messages.isEmpty() && !processService.isRunning(processId)) {
                TimeUnit.SECONDS.sleep(5);
            } else {
                transition(RUN, 0);
            }
        }
    }

    @GraphState(name = RUN)
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

        transition(CHECK);
    }

}
