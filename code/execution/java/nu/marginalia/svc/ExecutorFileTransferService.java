package nu.marginalia.svc;

import com.google.inject.Inject;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jooby.Context;

import java.io.IOException;
import java.io.OutputStream;
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
    public Object transferFile(Context context) throws SQLException, IOException {

        FileStorageId fileStorageId = FileStorageId.parse(context.path("fid").value(""));

        var fileStorage = fileStorageService.getStorage(fileStorageId);

        Path basePath = fileStorage.asPath();

        String path = context.query("path").value("").replaceAll("%2F", "/");
        Path filePath = basePath.resolve(path).normalize();

        logger.info("Request to transfer {} from {}", path, fileStorageId.id());

        // ensure filePath is within basePath
        // even though this is an internal API, it's better to be safe than sorry
        if (!filePath.startsWith(basePath)) {
            context.setResponseCode(403);
            return "Forbidden";
        }

        // Announce that we support byte ranges
        context.setResponseHeader("Accept-Ranges", "bytes");

        // Set the content type to binary
        context.setResponseType("application/octet-stream");

        String range = context.header("Range").valueOrNull();
        if (range != null) {
            String[] ranges = StringUtils.split(range, '=');
            if (ranges.length != 2 || !"bytes".equals(ranges[0])) {
                logger.warn("Invalid range header in {}: {}", filePath, range);
                context.setResponseCode(400);
                return "Invalid range header";
            }

            String[] rangeValues = StringUtils.split(ranges[1], '-');
            if (rangeValues.length != 2) {
                logger.warn("Invalid range header in {}: {}", filePath, range);
                context.setResponseCode(400);
                return "Invalid range header";
            }

            long start = Long.parseLong(rangeValues[0].trim());
            long end = Long.parseLong(rangeValues[1].trim());
            long contentLength = end - start + 1;
            context.setResponseHeader("Content-Range", "bytes " + start + "-" + end + "/" + Files.size(filePath));
            context.setResponseHeader("Content-Length", String.valueOf(contentLength));
            context.setResponseCode(206);

            if ("HEAD".equalsIgnoreCase(context.getMethod())) {
                return "";
            }

            serveFile(filePath, context.responseStream(), start, end + 1);
        } else {
            context.setResponseHeader("Content-Length", String.valueOf(Files.size(filePath)));
            context.setResponseCode(200);

            if ("HEAD".equalsIgnoreCase(context.getMethod())) {
                return "";
            }

            serveFile(filePath, context.responseStream());
        }

        return "";
    }

    private void serveFile(Path filePath, OutputStream outputStream) throws IOException {
        try (var is = Files.newInputStream(filePath); outputStream) {
            IOUtils.copy(is, outputStream);
        }
    }

    private void serveFile(Path filePath, OutputStream outputStream, long start, long end) throws IOException {
        try (var is = Files.newInputStream(filePath); outputStream) {
            IOUtils.copyLarge(is, outputStream, start, end - start);
        }
    }
}
