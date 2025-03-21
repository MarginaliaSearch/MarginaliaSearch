package nu.marginalia.executor;

import com.google.inject.Inject;
import nu.marginalia.execution.*;
import nu.marginalia.functions.favicon.FaviconGrpcService;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.SparkService;
import nu.marginalia.service.server.mq.MqRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.List;

// Weird name for this one to not have clashes with java.util.concurrent.ExecutorService
public class ExecutorSvc extends SparkService {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorSvc.class);
    private final ExecutionInit executionInit;

    @Inject
    public ExecutorSvc(BaseServiceParams params,
                       ExecutorGrpcService executorGrpcService,
                       ExecutorCrawlGrpcService executorCrawlGrpcService,
                       ExecutorSideloadGrpcService executorSideloadGrpcService,
                       ExecutorExportGrpcService executorExportGrpcService,
                       FaviconGrpcService faviconGrpcService,
                       ExecutionInit executionInit,
                       ExecutorFileTransferService fileTransferService) throws Exception {
        super(params,
                ServicePartition.partition(params.configuration.node()),
                List.of(executorGrpcService,
                        executorCrawlGrpcService,
                        executorSideloadGrpcService,
                        executorExportGrpcService,
                        faviconGrpcService)
            );

        this.executionInit = executionInit;

        Spark.get("/transfer/file/:fid", fileTransferService::transferFile);
        Spark.head("/transfer/file/:fid", fileTransferService::transferFile);
    }

    @MqRequest(endpoint="FIRST-BOOT")
    public void setUpDefaultActors(String message) throws Exception {
        logger.info("Initializing default actors");

        executionInit.initDefaultActors();
    }



}
