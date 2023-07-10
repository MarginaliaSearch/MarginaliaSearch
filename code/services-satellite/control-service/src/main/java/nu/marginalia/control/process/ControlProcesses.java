package nu.marginalia.control.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.model.ControlProcess;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.StateMachine;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public class ControlProcesses {
    private final MqPersistence persistence;
    private final ServiceEventLog eventLog;
    public Map<ControlProcess, StateMachine> stateMachines = new HashMap<>();

    @Inject
    public ControlProcesses(MqPersistence persistence,
                            BaseServiceParams baseServiceParams,
                            RepartitionReindexProcess repartitionReindexProcess
                            ) {
        this.persistence = persistence;
        this.eventLog = baseServiceParams.eventLog;

        register(ControlProcess.REPARTITION_REINDEX, repartitionReindexProcess);
    }

    private void register(ControlProcess process, AbstractStateGraph graph) {
        var sm = new StateMachine(persistence, process.id(), UUID.randomUUID(), graph);

        sm.listen((function, param) -> logStateChange(process, function));

        stateMachines.put(process, sm);
    }

    private void logStateChange(ControlProcess process, String state) {
        eventLog.logEvent("FSM-STATE-CHANGE", process.id() + " -> " + state);
    }

    public void start(ControlProcess process) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init();
    }

    public void resume(ControlProcess process) throws Exception {
        eventLog.logEvent("FSM-RESUME", process.id());
        stateMachines.get(process).resume();
    }
}
