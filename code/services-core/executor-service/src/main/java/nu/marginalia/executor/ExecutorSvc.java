package nu.marginalia.executor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.executor.svc.TransferService;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import nu.marginalia.service.server.mq.MqRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.List;

// Weird name for this one to not have clashes with java.util.concurrent.ExecutorService
public class ExecutorSvc extends Service {
    private final BaseServiceParams params;
    private final Gson gson;
    private final ExecutorActorControlService actorControlService;
    private final TransferService transferService;

    private static final Logger logger = LoggerFactory.getLogger(ExecutorSvc.class);

    @Inject

    public ExecutorSvc(BaseServiceParams params,
                       ExecutorActorControlService actorControlService,
                       ExecutorGrpcService executorGrpcService,
                       Gson gson,
                       TransferService transferService)
    {
        super(params, List.of(executorGrpcService));
        this.params = params;
        this.gson = gson;
        this.actorControlService = actorControlService;
        this.transferService = transferService;

        Spark.get("/transfer/file/:fid", transferService::transferFile);
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

}
