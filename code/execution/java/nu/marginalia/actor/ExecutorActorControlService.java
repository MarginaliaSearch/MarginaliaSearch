package nu.marginalia.actor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.monitor.FileStorageMonitorActor;
import nu.marginalia.actor.precession.ExportAllPrecessionActor;
import nu.marginalia.actor.proc.*;
import nu.marginalia.actor.prototype.ActorPrototype;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.task.*;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.BaseServiceParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** This class is responsible for starting and stopping the various actors in the responsible service */
@Singleton
public class ExecutorActorControlService {
    private final ServiceEventLog eventLog;
    private final MessageQueueFactory messageQueueFactory;
    private final ExecutorActorStateMachines stateMachines;
    public Map<ExecutorActor, ActorPrototype> actorDefinitions = new HashMap<>();
    private final int node;

    private final NodeConfiguration nodeConfiguration;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ExecutorActorControlService(MessageQueueFactory messageQueueFactory,
                                       ServiceConfiguration serviceConfiguration,
                                       NodeConfigurationService configurationService,
                                       BaseServiceParams baseServiceParams,
                                       ConvertActor convertActor,
                                       ConvertAndLoadActor convertAndLoadActor,
                                       CrawlActor crawlActor,
                                       LiveCrawlActor liveCrawlActor,
                                       RecrawlSingleDomainActor recrawlSingleDomainActor,
                                       RestoreBackupActor restoreBackupActor,
                                       ConverterMonitorActor converterMonitorFSM,
                                       CrawlerMonitorActor crawlerMonitorActor,
                                       LiveCrawlerMonitorActor liveCrawlerMonitorActor,
                                       LoaderMonitorActor loaderMonitor,
                                       ProcessLivenessMonitorActor processMonitorFSM,
                                       FileStorageMonitorActor fileStorageMonitorActor,
                                       IndexConstructorMonitorActor indexConstructorMonitorActor,
                                       TriggerAdjacencyCalculationActor triggerAdjacencyCalculationActor,
                                       ExportDataActor exportDataActor,
                                       ExportAtagsActor exportAtagsActor,
                                       ExportFeedsActor exportFeedsActor,
                                       ExportSampleDataActor exportSampleDataActor,
                                       ExportTermFreqActor exportTermFrequenciesActor,
                                       ExportSegmentationModelActor exportSegmentationModelActor,
                                       ExportTaskMonitorActor exportTasksMonitorActor,
                                       DownloadSampleActor downloadSampleActor,
                                       ScrapeFeedsActor scrapeFeedsActor,
                                       ExecutorActorStateMachines stateMachines,
                                       MigrateCrawlDataActor migrateCrawlDataActor,
                                       ExportAllPrecessionActor exportAllPrecessionActor,
                                       UpdateNsfwFiltersActor updateNsfwFiltersActor,
                                       UpdateRssActor updateRssActor) throws SQLException {
        this.messageQueueFactory = messageQueueFactory;
        this.eventLog = baseServiceParams.eventLog;
        this.stateMachines = stateMachines;
        this.node = baseServiceParams.configuration.node();

        this.nodeConfiguration = configurationService.get(node);

        register(ExecutorActor.CRAWL, crawlActor);
        register(ExecutorActor.LIVE_CRAWL, liveCrawlActor);
        register(ExecutorActor.RECRAWL_SINGLE_DOMAIN, recrawlSingleDomainActor);

        register(ExecutorActor.CONVERT, convertActor);
        register(ExecutorActor.RESTORE_BACKUP, restoreBackupActor);
        register(ExecutorActor.CONVERT_AND_LOAD, convertAndLoadActor);

        register(ExecutorActor.PROC_INDEX_CONSTRUCTOR_SPAWNER, indexConstructorMonitorActor);
        register(ExecutorActor.PROC_CONVERTER_SPAWNER, converterMonitorFSM);
        register(ExecutorActor.PROC_LOADER_SPAWNER, loaderMonitor);
        register(ExecutorActor.PROC_CRAWLER_SPAWNER, crawlerMonitorActor);
        register(ExecutorActor.PROC_LIVE_CRAWL_SPAWNER, liveCrawlerMonitorActor);
        register(ExecutorActor.PROC_EXPORT_TASKS_SPAWNER, exportTasksMonitorActor);

        register(ExecutorActor.MONITOR_PROCESS_LIVENESS, processMonitorFSM);
        register(ExecutorActor.MONITOR_FILE_STORAGE, fileStorageMonitorActor);

        register(ExecutorActor.ADJACENCY_CALCULATION, triggerAdjacencyCalculationActor);

        register(ExecutorActor.EXPORT_DATA, exportDataActor);
        register(ExecutorActor.EXPORT_ATAGS, exportAtagsActor);
        register(ExecutorActor.EXPORT_FEEDS, exportFeedsActor);
        register(ExecutorActor.EXPORT_SAMPLE_DATA, exportSampleDataActor);
        register(ExecutorActor.EXPORT_TERM_FREQUENCIES, exportTermFrequenciesActor);
        register(ExecutorActor.EXPORT_SEGMENTATION_MODEL, exportSegmentationModelActor);

        register(ExecutorActor.DOWNLOAD_SAMPLE, downloadSampleActor);

        register(ExecutorActor.SCRAPE_FEEDS, scrapeFeedsActor);
        register(ExecutorActor.UPDATE_RSS, updateRssActor);

        register(ExecutorActor.MIGRATE_CRAWL_DATA, migrateCrawlDataActor);
        register(ExecutorActor.SYNC_NSFW_LISTS, updateNsfwFiltersActor);

        if (serviceConfiguration.node() == 1) {
            register(ExecutorActor.PREC_EXPORT_ALL, exportAllPrecessionActor);
        }
    }

    private void register(ExecutorActor process, RecordActorPrototype graph) {

        if (!process.profileSet.contains(nodeConfiguration.profile())) {
            return;
        }

        var sm = new ActorStateMachine(messageQueueFactory, process.id(), node, UUID.randomUUID(), graph);
        sm.listen((function, param) -> logStateChange(process, function));

        stateMachines.put(process, sm);
        actorDefinitions.put(process, graph);
    }

    private void logStateChange(ExecutorActor process, String state) {
        if ("ERROR".equals(state)) {
            eventLog.logEvent("FSM-ERROR", process.id());
        }
    }

    public void start(ExecutorActor process) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.init(process);
    }

    public <T> void startFrom(ExecutorActor process, ActorStep step) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.initFrom(process, step);
    }

    public <T> void startFromJSON(ExecutorActor process, String state, String json) throws Exception {
        eventLog.logEvent("FSM-START", process.id());

        stateMachines.startFromJSON(process, state, json);
    }

    public void stop(ExecutorActor process) {
        eventLog.logEvent("FSM-STOP", process.id());

        try {
            stateMachines.stop(process);
        }
        catch (Exception e) {
            logger.error("Failed to stop FSM", e);
        }
    }

    public Map<ExecutorActor, ActorStateInstance> getActorStates() {
        return stateMachines.getActorStates();
    }

    public boolean isDirectlyInitializable(ExecutorActor actor) {
        return actorDefinitions.get(actor).isDirectlyInitializable();
    }

    public ActorPrototype getActorDefinition(ExecutorActor actor) {
        return actorDefinitions.get(actor);
    }

}
