package nu.marginalia.control.fsm.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.model.ProcessHeartbeat;
import nu.marginalia.control.svc.HeartbeatService;
import nu.marginalia.control.svc.ProcessService;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;

import java.util.concurrent.TimeUnit;

@Singleton
public class ProcessLivenessMonitorFSM extends AbstractStateGraph {

    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String MONITOR = "MONITOR";
    private static final String END = "END";
    private final ProcessService processService;
    private final HeartbeatService heartbeatService;


    @Inject
    public ProcessLivenessMonitorFSM(StateFactory stateFactory,
                                     ProcessService processService,
                                     HeartbeatService heartbeatService) {
        super(stateFactory);
        this.processService = processService;
        this.heartbeatService = heartbeatService;
    }

    @GraphState(name = INITIAL, next = MONITOR)
    public void init() {
    }

    @GraphState(name = MONITOR, resume = ResumeBehavior.RETRY)
    public void monitor() throws Exception {

        for (;;) {
            var processHeartbeats = heartbeatService.getProcessHeartbeats();

            processHeartbeats.stream()
                    .filter(ProcessHeartbeat::isRunning)
                    .filter(p -> !processService.isRunning(p.getProcessId()))
                    .forEach(heartbeatService::flagProcessAsStopped);

            TimeUnit.SECONDS.sleep(60);
        }
    }

}
