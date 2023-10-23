package nu.marginalia.control.actor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.actor.ActorStateMachine;
import nu.marginalia.actor.prototype.ActorPrototype;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.control.actor.rebalance.RebalanceActor;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Singleton
public class ControlActorService {
    private final ServiceEventLog eventLog;
    private final Gson gson;
    private final MessageQueueFactory messageQueueFactory;
    public Map<ControlActor, ActorStateMachine> stateMachines = new HashMap<>();
    public Map<ControlActor, ActorPrototype> actorDefinitions = new HashMap<>();
    private final int node;
    @Inject
    public ControlActorService(MessageQueueFactory messageQueueFactory,
                               BaseServiceParams baseServiceParams,
                               RebalanceActor rebalanceActor
    ) {
        this.messageQueueFactory = messageQueueFactory;
        this.eventLog = baseServiceParams.eventLog;
        this.gson = GsonFactory.get();
        this.node = baseServiceParams.configuration.node();

//        register(ControlActor.REBALANCE, rebalanceActor);
    }

    private void register(ControlActor process, ActorPrototype graph) {
        var sm = new ActorStateMachine(messageQueueFactory, process.id(), node, UUID.randomUUID(), graph);
        sm.listen((function, param) -> logStateChange(process, function));

        stateMachines.put(process, sm);
        actorDefinitions.put(process, graph);
    }

    private void logStateChange(ControlActor process, String state) {
        eventLog.logEvent("FSM-STATE-CHANGE", process.id() + " -> " + state);
    }

    public void startFrom(ControlActor process, String state) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state);
    }

    public void start(ControlActor process) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init();
    }

    public <T> void startFrom(ControlActor process, String state, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state, gson.toJson(arg));
    }

    public <T> void startFromJSON(ControlActor process, String state, String json) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state, json);
    }

    public <T> void start(ControlActor process, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init(gson.toJson(arg));
    }
    public <T> void startJSON(ControlActor process, String json) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init(json);
    }
    @SneakyThrows
    public void stop(ControlActor process) {
        eventLog.logEvent("FSM-STOP", process.id());

        stateMachines.get(process).abortExecution();
    }

    public Map<ControlActor, ActorStateInstance> getActorStates() {
        return stateMachines.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().getState())
        );
    }

    public boolean isDirectlyInitializable(ControlActor actor) {
        return actorDefinitions.get(actor).isDirectlyInitializable();
    }

    public ActorPrototype getActorDefinition(ControlActor actor) {
        return actorDefinitions.get(actor);
    }

}