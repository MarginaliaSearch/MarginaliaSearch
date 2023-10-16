package nu.marginalia.executor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.actor.Actor;
import nu.marginalia.actor.ActorApi;
import nu.marginalia.actor.ActorControlService;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.executor.model.ActorRunState;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.storage.FileStorageContent;
import nu.marginalia.executor.storage.FileStorageFile;
import nu.marginalia.executor.svc.BackupService;
import nu.marginalia.executor.svc.ProcessingService;
import nu.marginalia.executor.svc.SideloadService;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import nu.marginalia.service.server.mq.MqNotification;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Weird name for this one to not have clashes with java.util.concurrent.ExecutorService
public class ExecutorSvc extends Service {
    private final BaseServiceParams params;
    private final ActorControlService actorControlService;
    private final FileStorageService fileStorageService;

    private static final Logger logger = LoggerFactory.getLogger(ExecutorSvc.class);

    @Inject
    public ExecutorSvc(BaseServiceParams params,
                       ActorControlService actorControlService,
                       ProcessingService processingService,
                       SideloadService sideloadService,
                       BackupService backupService,
                       FileStorageService fileStorageService,
                       Gson gson,
                       ActorApi actorApi) {
        super(params);
        this.params = params;
        this.actorControlService = actorControlService;
        this.fileStorageService = fileStorageService;

        Spark.post("/actor/:id/start", actorApi::startActor);
        Spark.post("/actor/:id/start/:state", actorApi::startActorFromState);
        Spark.post("/actor/:id/stop", actorApi::stopActor);
        Spark.get("/actor", this::getActorStates, gson::toJson);

        Spark.post("/process/crawl/:fid", processingService::startCrawl);
        Spark.post("/process/recrawl", processingService::startRecrawl);
        Spark.post("/process/convert/:fid", processingService::startConversion);
        Spark.post("/process/convert-load/:fid", processingService::startConvertLoad);
        Spark.post("/process/crawl-spec/from-db", processingService::createCrawlSpecFromDb);
        Spark.post("/process/crawl-spec/from-download", processingService::createCrawlSpecFromDownload);
        Spark.post("/process/load", processingService::startLoad);
        Spark.post("/process/adjacency-calculation", processingService::startAdjacencyCalculation);

        Spark.post("/sideload/dirtree", sideloadService::sideloadDirtree);
        Spark.post("/sideload/stackexchange", sideloadService::sideloadStackexchange);
        Spark.post("/sideload/encyclopedia", sideloadService::sideloadEncyclopedia);

        Spark.post("/backup/:fid/restore", backupService::restore);
        Spark.get("/storage/:fid", this::listFiles, gson::toJson);

    }

    @MqNotification(endpoint="FIRST-BOOT")
    public void setUpDefaultActors(String message) throws Exception {
        logger.info("Initializing default actors");
        actorControlService.start(Actor.MONITOR_PROCESS_LIVENESS);
        actorControlService.start(Actor.MONITOR_FILE_STORAGE);
        actorControlService.start(Actor.MONITOR_MESSAGE_QUEUE);
        actorControlService.start(Actor.PROC_CONVERTER_SPAWNER);
        actorControlService.start(Actor.PROC_CRAWLER_SPAWNER);
        actorControlService.start(Actor.PROC_INDEX_CONSTRUCTOR_SPAWNER);
        actorControlService.start(Actor.PROC_LOADER_SPAWNER);
    }


    private FileStorageContent listFiles(Request request, Response response) throws SQLException, IOException {
        FileStorageId fileStorageId = FileStorageId.parse(request.params("fid"));

        var storage = fileStorageService.getStorage(fileStorageId);

        List<FileStorageFile> files;

        try (var fs = Files.list(storage.asPath())) {
            files = fs.filter(Files::isRegularFile)
                    .map(this::createFileModel)
                    .sorted(Comparator.comparing(FileStorageFile::name))
                    .toList();
        }

        return new FileStorageContent(files);
    }

    @SneakyThrows
    private FileStorageFile createFileModel(Path path) {
        return new FileStorageFile(
                path.toFile().getName(),
                Files.size(path),
                Files.getLastModifiedTime(path).toInstant().toString()
                );
    }


    private final ConcurrentHashMap<String, String> actorStateDescriptions = new ConcurrentHashMap<>();

    private ActorRunStates getActorStates(Request request, Response response) {
        var items = actorControlService.getActorStates().entrySet().stream().map(e -> {
                    final var stateGraph = actorControlService.getActorDefinition(e.getKey());

                    final ActorStateInstance state = e.getValue();
                    final String actorDescription = stateGraph.describe();

                    final String machineName = e.getKey().name();
                    final String stateName = state.name();

                    final String stateDescription = actorStateDescriptions.computeIfAbsent(
                            (machineName + "." + stateName),
                            k -> Optional.ofNullable(stateGraph.declaredStates().get(stateName))
                                    .map(ActorState::description)
                                    .orElse("Description missing for " + stateName)
                    );

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
