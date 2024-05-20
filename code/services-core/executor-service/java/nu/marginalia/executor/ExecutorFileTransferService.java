package nu.marginalia.executor;

import com.google.inject.Inject;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public class ExecutorFileTransferService {
    private final FileStorageService fileStorageService;
    private static final Logger logger = LoggerFactory.getLogger(ExecutorFileTransferService.class);

    @Inject
    public ExecutorFileTransferService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /** Allows transfer of files from each partition */
    public Object transferFile(Request request, Response response) throws SQLException, IOException {

        FileStorageId fileStorageId = FileStorageId.parse(request.params("fid"));

        var fileStorage = fileStorageService.getStorage(fileStorageId);

        Path basePath = fileStorage.asPath();

        String path = request.queryParams("path").replaceAll("%2F", "/");
        Path filePath = basePath.resolve(path).normalize();

        // ensure filePath is within basePath
        // even though this is an internal API, it's better to be safe than sorry
        if (!filePath.startsWith(basePath)) {
            response.status(403);
            return "Forbidden";
        }

        // Announce that we support byte ranges
        response.header("Accept-Ranges", "bytes");

        // Set the content type to binary
        response.type("application/octet-stream");

        String range = request.headers("Range");
        if (range != null) {
            String[] ranges = StringUtils.split(range, '=');
            if (ranges.length != 2 || !"bytes".equals(ranges[0])) {
                logger.warn("Invalid range header in {}: {}", filePath, range);
                Spark.halt(400, "Invalid range header");
            }

            String[] rangeValues = StringUtils.split(ranges[1], '-');
            if (rangeValues.length != 2) {
                logger.warn("Invalid range header in {}: {}", filePath, range);
                Spark.halt(400, "Invalid range header");
            }

            long start = Long.parseLong(rangeValues[0].trim());
            long end = Long.parseLong(rangeValues[1].trim());
            long contentLength = end - start + 1;
            response.header("Content-Range", "bytes " + start + "-" + end + "/" + Files.size(filePath));
            response.header("Content-Length", String.valueOf(contentLength));
            response.status(206);

            if ("HEAD".equalsIgnoreCase(request.requestMethod())) {
                return "";
            }

            serveFile(filePath, response.raw().getOutputStream(), start, end + 1);
        } else {
            response.header("Content-Length", String.valueOf(Files.size(filePath)));
            response.status(200);

            if ("HEAD".equalsIgnoreCase(request.requestMethod())) {
                return "";
            }

            serveFile(filePath, response.raw().getOutputStream());
        }

        return "";
    }

    private void serveFile(Path filePath, ServletOutputStream outputStream) throws IOException {
        try (var is = Files.newInputStream(filePath)) {
            IOUtils.copy(is, outputStream);
        }
    }

    private void serveFile(Path filePath, ServletOutputStream outputStream, long start, long end) throws IOException {
        try (var is = Files.newInputStream(filePath)) {
            IOUtils.copyLarge(is, outputStream, start, end - start);
        }
    }
}
