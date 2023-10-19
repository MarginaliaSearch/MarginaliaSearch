package nu.marginalia.executor.svc;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.executor.model.transfer.TransferItem;
import nu.marginalia.executor.model.transfer.TransferSpec;
import nu.marginalia.executor.storage.FileStorageContent;
import nu.marginalia.executor.storage.FileStorageFile;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TransferService {
    private final Gson gson;
    private final FileStorageService fileStorageService;
    private final HikariDataSource dataSource;
    private final int nodeId;

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);
    @Inject
    public TransferService(
            Gson gson,
            FileStorageService fileStorageService,
            HikariDataSource dataSource,
            ServiceConfiguration config)
    {
        this.gson = gson;
        this.fileStorageService = fileStorageService;
        this.dataSource = dataSource;
        this.nodeId = config.node();
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


    public FileStorageContent listFiles(Request request, Response response) throws SQLException, IOException {
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

    public TransferSpec getTransferSpec(Request request, Response response) throws SQLException {
        List<FileStorageId> fileStorageIds = fileStorageService.getActiveFileStorages(nodeId, FileStorageType.CRAWL_DATA);
        if (fileStorageIds.isEmpty()) {
            logger.warn("No ACTIVE crawl data");
            return new TransferSpec();
        }
        int count = Integer.parseInt(request.queryParams("count"));

        logger.info("Preparing a transfer of {} domains", count);

        List<TransferItem> items = new ArrayList<>();
        var storage = fileStorageService.getStorage(fileStorageIds.get(0));

        try (var conn = dataSource.getConnection();
             var query = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=? AND NODE_AFFINITY=" + nodeId)
        ) {
            for (var item : WorkLog.iterable(storage.asPath().resolve("crawler.log"))) {
                if (items.size() >= count)
                    break;

                if (!Files.isRegularFile(storage.asPath().resolve(item.relPath()))) {
                    logger.info("Ignoring absent item {}", item);
                    continue;
                }

                query.setString(1, item.id());
                var rs = query.executeQuery();
                if (rs.next()) {
                    items.add(new TransferItem(
                            item.id(),
                            rs.getInt(1),
                            fileStorageIds.get(0),
                            item.relPath()
                    ));
                }
                else {
                    logger.info("Rejected {}", item);
                }
            }
        }

        logger.info("Found {} eligible domains", items.size());

        return new TransferSpec(items);
    }

    public Object yieldDomain(Request request, Response response) throws SQLException, IOException {
        var item = gson.fromJson(request.body(), TransferItem.class);
        var storage = fileStorageService.getStorage(item.fileStorageId());
        Files.delete(storage.asPath().resolve(storage.asPath().resolve(item.path())));
        return "";
    }

    public void pruneCrawlDataMqEndpoint() throws SQLException, IOException {
        List<FileStorageId> fileStorageIds = fileStorageService.getActiveFileStorages(nodeId, FileStorageType.CRAWL_DATA);
        if (fileStorageIds.isEmpty()) {
            return;
        }
        var storage = fileStorageService.getStorage(fileStorageIds.get(0));

        Path newCrawlLogPath = storage.asPath().resolve("crawler.log-new");
        Path oldCrawlLogPath = storage.asPath().resolve("crawler.log");

        int pruned = 0;
        try (var newWorkLog = new WorkLog(newCrawlLogPath)) {
            for (var item : WorkLog.iterable(oldCrawlLogPath)) {
                if (Files.exists(storage.asPath().resolve(item.relPath()))) {
                    newWorkLog.setJobToFinished(item.id(), item.path(), item.cnt());
                }
                else {
                    pruned++;
                }
            }
        }
        if (pruned > 0) {
            logger.info("Pruned {} items from the crawl log!", pruned);
        }

        Files.move(newCrawlLogPath, oldCrawlLogPath, StandardCopyOption.REPLACE_EXISTING);
    }
}
