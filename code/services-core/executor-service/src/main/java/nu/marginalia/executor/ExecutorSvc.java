package nu.marginalia.executor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ActorApi;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.executor.model.ActorRunState;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.svc.*;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import nu.marginalia.service.server.mq.MqRequest;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

// Weird name for this one to not have clashes with java.util.concurrent.ExecutorService
public class ExecutorSvc extends Service {
    private final BaseServiceParams params;
    private final Gson gson;
    private final ExecutorActorControlService actorControlService;
    private final FileStorageService fileStorageService;
    private final TransferService transferService;

    private static final Logger logger = LoggerFactory.getLogger(ExecutorSvc.class);

    @Inject
    public ExecutorSvc(BaseServiceParams params,
                       ExecutorActorControlService actorControlService,
                       ProcessingService processingService,
                       SideloadService sideloadService,
                       BackupService backupService,
                       ExportService exportService,
                       FileStorageService fileStorageService,
                       Gson gson,
                       TransferService transferService,
                       ActorApi actorApi) {
        super(params);
        this.params = params;
        this.gson = gson;
        this.actorControlService = actorControlService;
        this.fileStorageService = fileStorageService;
        this.transferService = transferService;

        Spark.post("/actor/:id/start", actorApi::startActor);
        Spark.post("/actor/:id/start/:state", actorApi::startActorFromState);
        Spark.post("/actor/:id/stop", actorApi::stopActor);
        Spark.get("/actor", this::getActorStates, gson::toJson);

        Spark.post("/process/crawl/:fid", processingService::startCrawl);
        Spark.post("/process/recrawl", processingService::startRecrawl);
        Spark.post("/process/convert/:fid", processingService::startConversion);
        Spark.post("/process/convert-load/:fid", processingService::startConvertLoad);
        Spark.post("/process/crawl-spec/from-download", processingService::createCrawlSpecFromDownload);
        Spark.post("/process/load", processingService::startLoad);
        Spark.post("/process/adjacency-calculation", processingService::startAdjacencyCalculation);

        Spark.post("/sideload/dirtree", sideloadService::sideloadDirtree);
        Spark.post("/sideload/stackexchange", sideloadService::sideloadStackexchange);
        Spark.post("/sideload/encyclopedia", sideloadService::sideloadEncyclopedia);

        Spark.post("/export/atags", exportService::exportAtags);
        Spark.post("/export/data", exportService::exportData);

        Spark.post("/backup/:fid/restore", backupService::restore);
        Spark.get("/storage/:fid", transferService::listFiles, gson::toJson);

        Spark.get("/transfer/file/:fid", transferService::transferFile);

        Spark.get("/transfer/spec", transferService::getTransferSpec, gson::toJson);
        Spark.post("/transfer/yield", transferService::yieldDomain);
    }

    @MqRequest(endpoint="FIRST-BOOT")
    public void setUpDefaultActors(String message) throws Exception {
        logger.info("Initializing default actors");
        actorControlService.start(ExecutorActor.MONITOR_PROCESS_LIVENESS);
        actorControlService.start(ExecutorActor.MONITOR_FILE_STORAGE);
        actorControlService.start(ExecutorActor.PROC_CONVERTER_SPAWNER);
        actorControlService.start(ExecutorActor.PROC_CRAWLER_SPAWNER);
        actorControlService.start(ExecutorActor.PROC_INDEX_CONSTRUCTOR_SPAWNER);
        actorControlService.start(ExecutorActor.PROC_LOADER_SPAWNER);
    }

    @MqRequest(endpoint="TRANSFER-DOMAINS")
    public String transferDomains(String message) throws Exception {

        var spec = gson.fromJson(message, TransferService.TransferReq.class);

        synchronized (this) {
            transferService.transferMqEndpoint(spec.sourceNode(), spec.count());
        }

        return "OK";
    }


    @MqRequest(endpoint="PRUNE-CRAWL-DATA")
    public String pruneCrawlData(String message) throws SQLException, IOException {

        synchronized (this) { // would not be great if this ran in parallel with itself
            transferService.pruneCrawlDataMqEndpoint();
        }

        return "OK";
    }


    private final ConcurrentHashMap<String, String> actorStateDescriptions = new ConcurrentHashMap<>();

    private ActorRunStates getActorStates(Request request, Response response) {
        var items = actorControlService.getActorStates().entrySet().stream().map(e -> {
                    final var stateGraph = actorControlService.getActorDefinition(e.getKey());

                    final ActorStateInstance state = e.getValue();
                    final String actorDescription = stateGraph.describe();

                    final String machineName = e.getKey().name();
                    final String stateName = state.name();

                    final String stateDescription = "";

                    final boolean terminal = state.isFinal();
                    final boolean canStart = actorControlService.isDirectlyInitializable(e.getKey()) && terminal;

                    return new ActorRunState(machineName,
                            stateName,
                            actorDescription,
                            stateDescription,
                            terminal,
                            canStart);
                })
                .filter(s -> !s.terminal() || s.canStart())
                .sorted(Comparator.comparing(ActorRunState::name))
                .toList();

        return new ActorRunStates(params.configuration.node(), items);
    }


}
