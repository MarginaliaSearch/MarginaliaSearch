package nu.marginalia.control.actor.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.mqsm.StateFactory;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.graph.ResumeBehavior;
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
public class FileStorageMonitorActor extends AbstractStateGraph {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // STATES

    private static final String INITIAL = "INITIAL";
    private static final String MONITOR = "MONITOR";
    private static final String PURGE = "PURGE";
    private static final String REMOVE_STALE = "REMOVE-STALE";
    private static final String END = "END";
    private final FileStorageService fileStorageService;


    @Inject
    public FileStorageMonitorActor(StateFactory stateFactory,
                                   FileStorageService fileStorageService) {
        super(stateFactory);
        this.fileStorageService = fileStorageService;
    }

    @GraphState(name = INITIAL, next = MONITOR)
    public void init() {
    }

    @GraphState(name = MONITOR,
            next = PURGE,
            resume = ResumeBehavior.RETRY,
            transitions = { PURGE, REMOVE_STALE },
            description = """
                    Monitor the file storage and trigger at transition to PURGE if any file storage area
                    has been marked for deletion.
                    """)
    public void monitor() throws Exception {

        for (;;) {
            Optional<FileStorage> toDeleteOpt = fileStorageService.findFileStorageToDelete();

            if (toDeleteOpt.isPresent()) {
                transition(PURGE, toDeleteOpt.get().id());
            }

            List<FileStorage> allStorageItems = fileStorageService.getEachFileStorage();
            var missing = allStorageItems.stream().filter(storage -> !Files.exists(storage.asPath())).findAny();
            if (missing.isPresent()) {
                transition(REMOVE_STALE, missing.get().id());
            }

            fileStorageService.synchronizeStorageManifests(fileStorageService.getStorageBase(FileStorageBaseType.SLOW));

            TimeUnit.SECONDS.sleep(10);
        }
    }

    @GraphState(name = PURGE,
                next = MONITOR,
                resume = ResumeBehavior.RETRY,
                description = """
                        Purge the file storage area and transition back to MONITOR.
                        """
    )
    public void purge(FileStorageId id) throws Exception {
        var storage = fileStorageService.getStorage(id);
        logger.info("Deleting {} ", storage.path());
        Path path = storage.asPath();

        if (Files.exists(path)) {
            FileUtils.deleteDirectory(path.toFile());
        }

        fileStorageService.removeFileStorage(storage.id());
    }

    @GraphState(
            name = REMOVE_STALE,
            next = MONITOR,
            resume = ResumeBehavior.RETRY,
            description = """
                        Remove file storage from the database if it doesn't exist on disk.
                        """
    )
    public void removeStale(FileStorageId id) throws SQLException {
        fileStorageService.removeFileStorage(id);
    }
}
