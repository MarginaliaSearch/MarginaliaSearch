package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.Redirects;
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
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ControlFileStorageService( FileStorageService fileStorageService)
    {
        this.fileStorageService = fileStorageService;
    }

    public void register() throws IOException {
        Spark.get("/public/storage/:id/file", this::downloadFileFromStorage);
        Spark.post("/public/storage/:fid/delete", this::flagFileForDeletionRequest, Redirects.redirectToStorage);

    }

    public Object flagFileForDeletionRequest(Request request, Response response) throws SQLException {
        FileStorageId fid = new FileStorageId(Long.parseLong(request.params(":fid")));
        fileStorageService.flagFileForDeletion(fid);
        return "";
    }

    public Object downloadFileFromStorage(Request request, Response response) throws SQLException {
        var fileStorageId = FileStorageId.parse(request.params("id"));
        String filename = request.queryParams("name");

        Path root = fileStorageService.getStorage(fileStorageId).asPath();
        Path filePath = root.resolve(filename).normalize();

        if (!filePath.startsWith(root)) {
            response.status(403);
            return "";
        }

        if (filePath.endsWith(".txt") || filePath.endsWith(".log")) response.type("text/plain");
        else response.type("application/octet-stream");

        try (var is = Files.newInputStream(filePath)) {
            is.transferTo(response.raw().getOutputStream());
        }
        catch (IOException ex) {
            logger.error("Failed to download file", ex);
            throw new RuntimeException(ex);
        }

        return "";
    }
}
