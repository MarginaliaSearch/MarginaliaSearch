package nu.marginalia.control.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.control.model.*;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.*;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

@Singleton
public class ControlFileStorageService {
    private final HikariDataSource dataSource;
    private final FileStorageService fileStorageService;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ControlFileStorageService(HikariDataSource dataSource, FileStorageService fileStorageService) {
        this.dataSource = dataSource;
        this.fileStorageService = fileStorageService;
    }

    public Object flagFileForDeletionRequest(Request request, Response response) throws SQLException {
        FileStorageId fid = new FileStorageId(Long.parseLong(request.params(":fid")));
        flagFileForDeletion(fid);
        return "";
    }

    public void flagFileForDeletion(FileStorageId id) throws SQLException {
        try (var conn = dataSource.getConnection();
             var flagStmt = conn.prepareStatement("UPDATE FILE_STORAGE SET DO_PURGE = TRUE WHERE ID = ?")) {
            flagStmt.setLong(1, id.id());
            flagStmt.executeUpdate();
        }
    }

    @SneakyThrows
    public List<FileStorageBaseWithStorage> getStorageList() {
        var storageIds = getFileStorageIds();
        return makeFileStorageBaseWithStorage(storageIds);
    }

    @SneakyThrows
    public List<FileStorageBaseWithStorage> getStorageList(FileStorageType type) {
        var storageIds = getFileStorageIds(type);
        return makeFileStorageBaseWithStorage(storageIds);
    }

    private List<FileStorageId> getFileStorageIds() throws SQLException {
        List<FileStorageId> storageIds = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var storageByIdStmt = conn.prepareStatement("SELECT ID FROM FILE_STORAGE")) {
            var rs = storageByIdStmt.executeQuery();
            while (rs.next()) {
                storageIds.add(new FileStorageId(rs.getLong("ID")));
            }
        }

        return storageIds;
    }

    private List<FileStorageId> getFileStorageIds(FileStorageType type) throws SQLException {
        List<FileStorageId> storageIds = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var storageByIdStmt = conn.prepareStatement("SELECT ID FROM FILE_STORAGE WHERE TYPE = ?")) {
            storageByIdStmt.setString(1, type.name());
            var rs = storageByIdStmt.executeQuery();
            while (rs.next()) {
                storageIds.add(new FileStorageId(rs.getLong("ID")));
            }
        }

        return storageIds;
    }

    private List<FileStorageBaseWithStorage> makeFileStorageBaseWithStorage(List<FileStorageId> storageIds) throws SQLException {

        Map<FileStorageBaseId, FileStorageBase> fileStorageBaseByBaseId = new HashMap<>();
        Map<FileStorageBaseId, List<FileStorageWithActions>> fileStoragByBaseId = new HashMap<>();

        for (var id : storageIds) {
            var storage = fileStorageService.getStorage(id);
            fileStorageBaseByBaseId.computeIfAbsent(storage.base().id(), k -> storage.base());
            fileStoragByBaseId.computeIfAbsent(storage.base().id(), k -> new ArrayList<>()).add(new FileStorageWithActions(storage));
        }

        List<FileStorageBaseWithStorage> result = new ArrayList<>();
        for (var baseId : fileStorageBaseByBaseId.keySet()) {
            result.add(new FileStorageBaseWithStorage(fileStorageBaseByBaseId.get(baseId),
                    fileStoragByBaseId.get(baseId)

            ));
        }

        return result;
    }

    public FileStorageWithRelatedEntries getFileStorageWithRelatedEntries(FileStorageId id) throws SQLException {
        var storage = fileStorageService.getStorage(id);
        var related = getRelatedEntries(id);

        List<FileStorageFileModel> files = new ArrayList<>();

        try (var filesStream = Files.list(storage.asPath())) {
            filesStream
                    .filter(Files::isRegularFile)
                    .map(this::createFileModel)
                    .sorted(Comparator.comparing(FileStorageFileModel::filename))
                    .forEach(files::add);
        }
        catch (IOException ex) {
            logger.error("Failed to list files in storage", ex);
        }

        return new FileStorageWithRelatedEntries(new FileStorageWithActions(storage), related, files);
    }

    private FileStorageFileModel createFileModel(Path p) {
        try {
            String mTime = Files.getLastModifiedTime(p).toInstant().toString();
            String size;
            if (Files.isDirectory(p)) {
                size = "-";
            }
            else {
                long sizeBytes = Files.size(p);

                if (sizeBytes < 1024) size = sizeBytes + " B";
                else if (sizeBytes < 1024 * 1024) size = sizeBytes / 1024 + " KB";
                else if (sizeBytes < 1024 * 1024 * 1024) size = sizeBytes / (1024 * 1024) + " MB";
                else size = sizeBytes / (1024 * 1024 * 1024) + " GB";
            }

            return new FileStorageFileModel(p.toFile().getName(), mTime, size);
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    private List<FileStorage> getRelatedEntries(FileStorageId id) {
        List<FileStorage> ret = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var relatedIds = conn.prepareStatement("""
                     (SELECT SOURCE_ID AS ID FROM FILE_STORAGE_RELATION WHERE TARGET_ID = ?)
                     UNION
                     (SELECT TARGET_ID AS ID FROM FILE_STORAGE_RELATION WHERE SOURCE_ID = ?)
                     """))
        {

            relatedIds.setLong(1, id.id());
            relatedIds.setLong(2, id.id());
            var rs = relatedIds.executeQuery();
            while (rs.next()) {
                ret.add(fileStorageService.getStorage(new FileStorageId(rs.getLong("ID"))));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return ret;
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
