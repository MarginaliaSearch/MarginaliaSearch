package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.client.Context;
import nu.marginalia.control.Redirects;
import nu.marginalia.executor.client.ExecutorClient;
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

@Singleton
public class ControlFileStorageService {
    private final FileStorageService fileStorageService;
    private final ExecutorClient executorClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ControlFileStorageService(FileStorageService fileStorageService, ExecutorClient executorClient)
    {
        this.fileStorageService = fileStorageService;
        this.executorClient = executorClient;
    }

    public void register() throws IOException {
        Spark.post("/public/storage/:fid/delete", this::flagFileForDeletionRequest, Redirects.redirectToStorage);

    }

    public Object flagFileForDeletionRequest(Request request, Response response) throws SQLException {
        FileStorageId fid = new FileStorageId(Long.parseLong(request.params(":fid")));
        fileStorageService.flagFileForDeletion(fid);
        return "";
    }


}
