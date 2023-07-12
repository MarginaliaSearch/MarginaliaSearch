package nu.marginalia.control.process;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.model.ControlProcess;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MqFactory;
import nu.marginalia.mqsm.StateMachine;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public class ControlProcesses {
    private final ServiceEventLog eventLog;
    private final Gson gson;
    private final MqFactory messageQueueFactory;
    public Map<ControlProcess, StateMachine> stateMachines = new HashMap<>();

    @Inject
    public ControlProcesses(MqFactory messageQueueFactory,
                            GsonFactory gsonFactory,
                            BaseServiceParams baseServiceParams,
                            RepartitionReindexProcess repartitionReindexProcess,
                            ReconvertAndLoadProcess reconvertAndLoadProcess
                            ) {
        this.messageQueueFactory = messageQueueFactory;
        this.eventLog = baseServiceParams.eventLog;
        this.gson = gsonFactory.get();
        register(ControlProcess.REPARTITION_REINDEX, repartitionReindexProcess);
        register(ControlProcess.RECONVERT_LOAD, reconvertAndLoadProcess);
    }

    private void register(ControlProcess process, AbstractStateGraph graph) {
        var sm = new StateMachine(messageQueueFactory, process.id(), UUID.randomUUID(), graph);

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

    public <T> void start(ControlProcess process, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init(gson.toJson(arg));
    }

    public void resume(ControlProcess process) throws Exception {
        eventLog.logEvent("FSM-RESUME", process.id());
        stateMachines.get(process).resume();
    }
}
