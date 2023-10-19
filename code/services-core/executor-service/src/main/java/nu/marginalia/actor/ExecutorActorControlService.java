package nu.marginalia.actor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import nu.marginalia.actor.monitor.*;
import nu.marginalia.actor.proc.*;
import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.actor.task.*;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.server.BaseServiceParams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** This class is responsible for starting and stopping the various actors in the responsible service */
@Singleton
public class ExecutorActorControlService {
    private final ServiceEventLog eventLog;
    private final Gson gson;
    private final MessageQueueFactory messageQueueFactory;
    public Map<ExecutorActor, ActorStateMachine> stateMachines = new HashMap<>();
    public Map<ExecutorActor, AbstractActorPrototype> actorDefinitions = new HashMap<>();
    private final int node;
    @Inject
    public ExecutorActorControlService(MessageQueueFactory messageQueueFactory,
                                       BaseServiceParams baseServiceParams,
                                       ConvertActor convertActor,
                                       ConvertAndLoadActor convertAndLoadActor,
                                       CrawlActor crawlActor,
                                       RecrawlActor recrawlActor,
                                       RestoreBackupActor restoreBackupActor,
                                       ConverterMonitorActor converterMonitorFSM,
                                       CrawlerMonitorActor crawlerMonitorActor,
                                       LoaderMonitorActor loaderMonitor,
                                       MessageQueueMonitorActor messageQueueMonitor,
                                       ProcessLivenessMonitorActor processMonitorFSM,
                                       FileStorageMonitorActor fileStorageMonitorActor,
                                       IndexConstructorMonitorActor indexConstructorMonitorActor,
                                       TriggerAdjacencyCalculationActor triggerAdjacencyCalculationActor,
                                       CrawlJobExtractorActor crawlJobExtractorActor,
                                       ExportDataActor exportDataActor,
                                       TruncateLinkDatabase truncateLinkDatabase,
                                       TransferDomainsActor transferDomainsActor
                            ) {
        this.messageQueueFactory = messageQueueFactory;
        this.eventLog = baseServiceParams.eventLog;
        this.gson = GsonFactory.get();
        this.node = baseServiceParams.configuration.node();

        register(ExecutorActor.CRAWL, crawlActor);
        register(ExecutorActor.RECRAWL, recrawlActor);
        register(ExecutorActor.CONVERT, convertActor);
        register(ExecutorActor.RESTORE_BACKUP, restoreBackupActor);
        register(ExecutorActor.CONVERT_AND_LOAD, convertAndLoadActor);

        register(ExecutorActor.PROC_INDEX_CONSTRUCTOR_SPAWNER, indexConstructorMonitorActor);
        register(ExecutorActor.PROC_CONVERTER_SPAWNER, converterMonitorFSM);
        register(ExecutorActor.PROC_LOADER_SPAWNER, loaderMonitor);
        register(ExecutorActor.PROC_CRAWLER_SPAWNER, crawlerMonitorActor);

        register(ExecutorActor.MONITOR_MESSAGE_QUEUE, messageQueueMonitor);
        register(ExecutorActor.MONITOR_PROCESS_LIVENESS, processMonitorFSM);
        register(ExecutorActor.MONITOR_FILE_STORAGE, fileStorageMonitorActor);

        register(ExecutorActor.ADJACENCY_CALCULATION, triggerAdjacencyCalculationActor);
        register(ExecutorActor.CRAWL_JOB_EXTRACTOR, crawlJobExtractorActor);
        register(ExecutorActor.EXPORT_DATA, exportDataActor);
        register(ExecutorActor.TRUNCATE_LINK_DATABASE, truncateLinkDatabase);
        register(ExecutorActor.TRANSFER_DOMAINS, transferDomainsActor);
    }

    private void register(ExecutorActor process, AbstractActorPrototype graph) {
        var sm = new ActorStateMachine(messageQueueFactory, process.id(), node, UUID.randomUUID(), graph);
        sm.listen((function, param) -> logStateChange(process, function));

        stateMachines.put(process, sm);
        actorDefinitions.put(process, graph);
    }

    private void logStateChange(ExecutorActor process, String state) {
        eventLog.logEvent("FSM-STATE-CHANGE", process.id() + " -> " + state);
    }

    public void startFrom(ExecutorActor process, String state) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state);
    }

    public void start(ExecutorActor process) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init();
    }

    public <T> void startFrom(ExecutorActor process, String state, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state, gson.toJson(arg));
    }

    public <T> void startFromJSON(ExecutorActor process, String state, String json) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).initFrom(state, json);
    }

    public <T> void start(ExecutorActor process, Object arg) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init(gson.toJson(arg));
    }
    public <T> void startJSON(ExecutorActor process, String json) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.get(process).init(json);
    }
    @SneakyThrows
    public void stop(ExecutorActor process) {
        eventLog.logEvent("FSM-STOP", process.id());

        stateMachines.get(process).abortExecution();
    }

    public Map<ExecutorActor, ActorStateInstance> getActorStates() {
        return stateMachines.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().getState())
        );
    }

    public boolean isDirectlyInitializable(ExecutorActor actor) {
        return actorDefinitions.get(actor).isDirectlyInitializable();
    }

    public AbstractActorPrototype getActorDefinition(ExecutorActor actor) {
        return actorDefinitions.get(actor);
    }

}
