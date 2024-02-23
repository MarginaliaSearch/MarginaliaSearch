package nu.marginalia.executor;

import com.google.inject.Inject;
import nu.marginalia.execution.*;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;
import nu.marginalia.service.server.mq.MqRequest;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

// Weird name for this one to not have clashes with java.util.concurrent.ExecutorService
public class ExecutorSvc extends Service {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorSvc.class);
    private final ExecutionInit executionInit;
    private final FileStorageService fileStorageService;

    @Inject
    public ExecutorSvc(BaseServiceParams params,
                       ExecutorGrpcService executorGrpcService,
                       ExecutorCrawlGrpcService executorCrawlGrpcService,
                       ExecutorSideloadGrpcService executorSideloadGrpcService,
                       ExecutorExportGrpcService executorExportGrpcService,
                       ExecutionInit executionInit,
                       FileStorageService fileStorageService)
    {
        super(params,
                ServicePartition.partition(params.configuration.node()),
                List.of(executorGrpcService,
                        executorCrawlGrpcService,
                        executorSideloadGrpcService,
                        executorExportGrpcService)
            );

        this.executionInit = executionInit;
        this.fileStorageService = fileStorageService;

        Spark.get("/transfer/file/:fid", this::transferFile);
    }

    @MqRequest(endpoint="FIRST-BOOT")
    public void setUpDefaultActors(String message) throws Exception {
        logger.info("Initializing default actors");

        executionInit.initDefaultActors();
    }

    /** Allows transfer of files from each partition */
    private Object transferFile(Request request, Response response) throws SQLException, IOException {
        FileStorageId fileStorageId = FileStorageId.parse(request.params("fid"));

        var fileStorage = fileStorageService.getStorage(fileStorageId);

        Path basePath = fileStorage.asPath();
        // This is not a public API so injection isn't a concern
        Path filePath = basePath.resolve(request.queryParams("path"));

        response.type("application/octet-stream");
        FileUtils.copyFile(filePath.toFile(), response.raw().getOutputStream());
        return "";
    }

}
