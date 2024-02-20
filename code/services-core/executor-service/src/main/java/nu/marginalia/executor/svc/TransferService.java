package nu.marginalia.executor.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.executor.api.RpcFileStorageContent;
import nu.marginalia.executor.api.RpcFileStorageEntry;
import nu.marginalia.executor.api.RpcFileStorageId;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;

public class TransferService {
    private final Gson gson;
    private final FileStorageService fileStorageService;
    private final HikariDataSource dataSource;
    private final ExecutorClient executorClient;
    private final MqPersistence persistence;
    private final String executorServiceName;
    private final int nodeId;

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);
    @Inject
    public TransferService(
            Gson gson,
            FileStorageService fileStorageService,
            HikariDataSource dataSource,
            ExecutorClient executorClient, MqPersistence persistence, ServiceConfiguration config)
    {
        this.gson = gson;
        this.fileStorageService = fileStorageService;
        this.dataSource = dataSource;
        this.executorClient = executorClient;
        this.persistence = persistence;
        this.nodeId = config.node();
        this.executorServiceName = config.serviceName();
    }

    public Object transferFile(Request request, Response response) throws SQLException, IOException {
        FileStorageId fileStorageId = FileStorageId.parse(request.params("fid"));

        var fileStorage = fileStorageService.getStorage(fileStorageId);

        Path basePath = fileStorage.asPath();
        // This is not a public API so injection isn't a concern
        Path filePath = basePath.resolve(request.queryParams("path"));

        response.type("application/octet-stream");
        FileUtils.copyFile(filePath.toFile(), response.raw().getOutputStream());
        return "";
    }


    public RpcFileStorageContent listFiles(RpcFileStorageId request) throws SQLException, IOException {
        FileStorageId fileStorageId = FileStorageId.of(request.getFileStorageId());

        var storage = fileStorageService.getStorage(fileStorageId);

        var builder = RpcFileStorageContent.newBuilder();


        try (var fs = Files.list(storage.asPath())) {
            fs.filter(Files::isRegularFile)
                    .map(this::createFileModel)
                    .sorted(Comparator.comparing(RpcFileStorageEntry::getName))
                    .forEach(builder::addEntries);
        }

        return builder.build();
    }

    @SneakyThrows
    private RpcFileStorageEntry createFileModel(Path path) {
        return RpcFileStorageEntry.newBuilder()
                .setName(path.toFile().getName())
                .setSize(Files.size(path))
                .setLastModifiedTime(Files.getLastModifiedTime(path).toInstant().toString())
                .build();
    }


    public record TransferReq(int sourceNode, int count) { }
}
