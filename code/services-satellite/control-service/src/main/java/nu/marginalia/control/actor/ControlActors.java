package nu.marginalia.control.actor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.control.actor.task.CrawlActor;
import nu.marginalia.control.actor.task.RecrawlActor;
import nu.marginalia.control.model.Actor;
import nu.marginalia.control.actor.monitor.*;
import nu.marginalia.control.actor.monitor.ConverterMonitorActor;
import nu.marginalia.control.actor.monitor.LoaderMonitorActor;
import nu.marginalia.control.actor.task.ReconvertAndLoadActor;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqsm.StateMachine;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.state.MachineState;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** This class is responsible for starting and stopping the various actors in the controller service */
@Singleton
public class ControlActors {
    private final ServiceEventLog eventLog;
    private final Gson gson;
    private final MessageQueueFactory messageQueueFactory;
    public Map<Actor, StateMachine> stateMachines = new HashMap<>();
    public Map<Actor, AbstractStateGraph> actorDefinitions = new HashMap<>();

    @Inject
    public ControlActors(MessageQueueFactory messageQueueFactory,
                         GsonFactory gsonFactory,
                         BaseServiceParams baseServiceParams,
                         ReconvertAndLoadActor reconvertAndLoadActor,
                         CrawlActor crawlActor,
                         RecrawlActor recrawlActor,
                         ConverterMonitorActor converterMonitorFSM,
                         CrawlerMonitorActor crawlerMonitorActor,
                         LoaderMonitorActor loaderMonitor,
                         MessageQueueMonitorActor messageQueueMonitor,
                         ProcessLivenessMonitorActor processMonitorFSM,
                         FileStorageMonitorActor fileStorageMonitorActor
                            ) {
        this.messageQueueFactory = messageQueueFactory;
        this.eventLog = baseServiceParams.eventLog;
        this.gson = gsonFactory.get();

        register(Actor.CRAWL, crawlActor);
        register(Actor.RECRAWL, recrawlActor);
        register(Actor.RECONVERT_LOAD, reconvertAndLoadActor);
        register(Actor.CONVERTER_MONITOR, converterMonitorFSM);
        register(Actor.LOADER_MONITOR, loaderMonitor);
        register(Actor.CRAWLER_MONITOR, crawlerMonitorActor);
        register(Actor.MESSAGE_QUEUE_MONITOR, messageQueueMonitor);
        register(Actor.PROCESS_LIVENESS_MONITOR, processMonitorFSM);
        register(Actor.FILE_STORAGE_MONITOR, fileStorageMonitorActor);
    }

    private void register(Actor process, AbstractStateGraph graph) {
        var sm = new StateMachine(messageQueueFactory, process.id(), UUID.randomUUID(), graph);
        sm.listen((function, param) -> logStateChange(process, function));

        stateMachines.put(process, sm);
        actorDefinitions.put(process, graph);
    }

    private void logStateChange(Actor process, String state) {
        eventLog.logEvent("FSM-STATE-CHANGE", process.id() + " -> " + state);
    }

    public void startFrom(Actor process, String state) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state);
    }

    public void start(Actor process) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init();
    }

    public <T> void startFrom(Actor process, String state, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state, gson.toJson(arg));
    }

    public <T> void start(Actor process, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init(gson.toJson(arg));
    }

    @SneakyThrows
    public void stop(Actor fsm) {
        stateMachines.get(fsm).abortExecution();
    }

    public Map<Actor, MachineState> getActorStates() {
        return stateMachines.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().getState())
        );
    }

    public AbstractStateGraph getActorDefinition(Actor actor) {
        return actorDefinitions.get(actor);
    }

}