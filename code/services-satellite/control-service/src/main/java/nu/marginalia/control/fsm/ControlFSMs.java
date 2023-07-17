package nu.marginalia.control.fsm;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.control.model.ControlProcess;
import nu.marginalia.control.model.ControlProcessState;
import nu.marginalia.control.fsm.monitor.*;
import nu.marginalia.control.fsm.monitor.ConverterMonitorFSM;
import nu.marginalia.control.fsm.monitor.LoaderMonitorFSM;
import nu.marginalia.control.fsm.task.ReconvertAndLoadFSM;
import nu.marginalia.control.fsm.task.RepartitionReindexFSM;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqsm.StateMachine;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.state.MachineState;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class ControlFSMs {
    private final ServiceEventLog eventLog;
    private final Gson gson;
    private final MessageQueueFactory messageQueueFactory;
    public Map<ControlProcess, StateMachine> stateMachines = new HashMap<>();

    @Inject
    public ControlFSMs(MessageQueueFactory messageQueueFactory,
                       GsonFactory gsonFactory,
                       BaseServiceParams baseServiceParams,
                       RepartitionReindexFSM repartitionReindexFSM,
                       ReconvertAndLoadFSM reconvertAndLoadFSM,
                       ConverterMonitorFSM converterMonitorFSM,
                       LoaderMonitorFSM loaderMonitor,
                       MessageQueueMonitorFSM messageQueueMonitor,
                       ProcessLivenessMonitorFSM processMonitorFSM,
                       FileStorageMonitorFSM fileStorageMonitorFSM
                            ) {
        this.messageQueueFactory = messageQueueFactory;
        this.eventLog = baseServiceParams.eventLog;
        this.gson = gsonFactory.get();

        register(ControlProcess.REPARTITION_REINDEX, repartitionReindexFSM);
        register(ControlProcess.RECONVERT_LOAD, reconvertAndLoadFSM);
        register(ControlProcess.CONVERTER_MONITOR, converterMonitorFSM);
        register(ControlProcess.LOADER_MONITOR, loaderMonitor);
        register(ControlProcess.MESSAGE_QUEUE_MONITOR, messageQueueMonitor);
        register(ControlProcess.PROCESS_LIVENESS_MONITOR, processMonitorFSM);
        register(ControlProcess.FILE_STORAGE_MONITOR, fileStorageMonitorFSM);
    }

    private void register(ControlProcess process, AbstractStateGraph graph) {
        var sm = new StateMachine(messageQueueFactory, process.id(), UUID.randomUUID(), graph);

        sm.listen((function, param) -> logStateChange(process, function));

        stateMachines.put(process, sm);
    }

    private void logStateChange(ControlProcess process, String state) {
        eventLog.logEvent("FSM-STATE-CHANGE", process.id() + " -> " + state);
    }

    public void startFrom(ControlProcess process, String state) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state);
    }

    public void start(ControlProcess process) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init();
    }

    public <T> void startFrom(ControlProcess process, String state, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state, gson.toJson(arg));
    }

    public <T> void start(ControlProcess process, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init(gson.toJson(arg));
    }

    @SneakyThrows
    public void stop(ControlProcess fsm) {
        stateMachines.get(fsm).abortExecution();
    }

    public Map<ControlProcess, MachineState> getMachineStates() {
        return stateMachines.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().getState())
        );
    }
}
