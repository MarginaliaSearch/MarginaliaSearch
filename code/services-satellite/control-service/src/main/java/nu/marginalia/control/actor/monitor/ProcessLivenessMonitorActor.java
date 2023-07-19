package nu.marginalia.control.actor.monitor;

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
public class ProcessLivenessMonitorActor extends AbstractStateGraph {

    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String MONITOR = "MONITOR";
    private static final String END = "END";
    private final ProcessService processService;
    private final HeartbeatService heartbeatService;


    @Inject
    public ProcessLivenessMonitorActor(StateFactory stateFactory,
                                       ProcessService processService,
                                       HeartbeatService heartbeatService) {
        super(stateFactory);
        this.processService = processService;
        this.heartbeatService = heartbeatService;
    }

    @GraphState(name = INITIAL, next = MONITOR)
    public void init() {
    }

    @GraphState(name = MONITOR, next = MONITOR, resume = ResumeBehavior.RETRY, description = """
            Periodically check to ensure that the control service's view of
            running processes is agreement with the process heartbeats table.
             
            If the process is not running, mark the process as stopped in the table.
            """)
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
