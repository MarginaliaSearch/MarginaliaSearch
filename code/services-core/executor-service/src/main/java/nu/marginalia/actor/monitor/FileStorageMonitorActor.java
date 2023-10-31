package nu.marginalia.actor.monitor;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.actor.state.Resume;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class FileStorageMonitorActor extends RecordActorPrototype {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HikariDataSource dataSource;
    private final FileStorageService fileStorageService;
    private final int node;

    public record Initial() implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RETRY)
    public record Monitor() implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record Purge(FileStorageId id) implements ActorStep {}
    @Resume(behavior=ActorResumeBehavior.RESTART)
    public record RemoveStale(FileStorageId id) implements ActorStep {}
    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch (self) {
            case Initial i -> new Monitor();
            case Purge (FileStorageId id) -> {
                var storage = fileStorageService.getStorage(id);
                logger.info("Deleting {} ", storage.path());
                Path path = storage.asPath();

                if (Files.exists(path)) {
                    FileUtils.deleteDirectory(path.toFile());
                }

                fileStorageService.deregisterFileStorage(storage.id());
                yield new Monitor();
            }
            case RemoveStale(FileStorageId id) -> {
                fileStorageService.deregisterFileStorage(id);
                yield new Monitor();
            }
            case Monitor m -> {
                for (;;) {
                    Optional<FileStorage> toDeleteOpt = findFileStorageToDelete();

                    if (toDeleteOpt.isPresent()) {
                        yield new Purge(toDeleteOpt.get().id());
                    }

                    List<FileStorage> allStorageItems = fileStorageService.getEachFileStorage();
                    var missing = allStorageItems.stream().filter(storage -> !Files.exists(storage.asPath())).findAny();
                    if (missing.isPresent()) {
                        yield new RemoveStale(missing.get().id());
                    }

                    fileStorageService.synchronizeStorageManifests(fileStorageService.getStorageBase(FileStorageBaseType.STORAGE));
                    fileStorageService.synchronizeStorageManifests(fileStorageService.getStorageBase(FileStorageBaseType.BACKUP));

                    TimeUnit.SECONDS.sleep(10);
                }
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Monitor the file storage directories and purge any file storage area that has been marked for deletion," +
                " and remove any file storage area that is missing from disk.";
    }

    @Inject
    public FileStorageMonitorActor(Gson gson,
                                   HikariDataSource dataSource,
                                   ServiceConfiguration serviceConfiguration,
                                   FileStorageService fileStorageService) {
        super(gson);
        this.dataSource = dataSource;
        this.fileStorageService = fileStorageService;
        this.node = serviceConfiguration.node();
    }

    public Optional<FileStorage> findFileStorageToDelete() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT FILE_STORAGE.ID FROM FILE_STORAGE
                INNER JOIN FILE_STORAGE_BASE ON BASE_ID=FILE_STORAGE_BASE.ID
                WHERE STATE='DELETE'
                AND NODE = ?
                LIMIT 1
                """)) {

            stmt.setInt(1, node);

            var rs = stmt.executeQuery();
            if (rs.next()) {
                var id = new FileStorageId(rs.getLong("ID"));
                return Optional.of(fileStorageService.getStorage(id));
            }

        } catch (SQLException e) {
            logger.warn("SQL error", e);
        }

        return Optional.empty();
    }

}
