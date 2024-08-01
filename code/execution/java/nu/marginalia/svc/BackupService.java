package nu.marginalia.svc;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.inject.Inject;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.linkdb.LinkdbFileNames;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class BackupService {

    private final FileStorageService storageService;
    private final ServiceHeartbeat serviceHeartbeat;

    public enum BackupHeartbeatSteps {
        LINKS,
        DOCS,
        JOURNAL,
        DONE
    }

    @Inject
    public BackupService(FileStorageService storageService,
                         ServiceHeartbeat serviceHeartbeat) {
        this.storageService = storageService;
        this.serviceHeartbeat = serviceHeartbeat;
    }

    /** Create a new backup of the contents in the _STAGING storage areas.
     * This backup can later be dehydrated and quickly loaded into _LIVE.
     * */
    public void createBackupFromStaging(List<FileStorageId> associatedIds) throws SQLException, IOException {
        String desc = "Pre-load backup snapshot " + LocalDateTime.now();

        var backupStorage = storageService.allocateStorage(
                FileStorageType.BACKUP, "snapshot", desc);

        for (var associatedId : associatedIds) {
            storageService.relateFileStorages(associatedId, backupStorage.id());
        }

        var indexStagingStorage = IndexLocations.getIndexConstructionArea(storageService);
        var linkdbStagingStorage = IndexLocations.getLinkdbWritePath(storageService);


        try (var heartbeat = serviceHeartbeat.createServiceTaskHeartbeat(BackupHeartbeatSteps.class, "Backup")) {
            heartbeat.progress(BackupHeartbeatSteps.DOCS);
            backupFileCompressed(LinkdbFileNames.DOCDB_FILE_NAME, linkdbStagingStorage, backupStorage.asPath());

            heartbeat.progress(BackupHeartbeatSteps.LINKS);
            backupFileCompressed(LinkdbFileNames.DOMAIN_LINKS_FILE_NAME, linkdbStagingStorage, backupStorage.asPath());

            heartbeat.progress(BackupHeartbeatSteps.JOURNAL);
            // This file format is already compressed
            backupJournal(indexStagingStorage, backupStorage.asPath());
            
            heartbeat.progress(BackupHeartbeatSteps.DONE);
        }


    }


    /** Read back a backup into _STAGING */
    public void restoreBackup(FileStorageId backupId) throws SQLException, IOException {
        var backupStorage = storageService.getStorage(backupId).asPath();

        var indexStagingStorage = IndexLocations.getIndexConstructionArea(storageService);
        var linkdbStagingStorage = IndexLocations.getLinkdbWritePath(storageService);

        try (var heartbeat = serviceHeartbeat.createServiceTaskHeartbeat(BackupHeartbeatSteps.class, "Restore Backup")) {
            heartbeat.progress(BackupHeartbeatSteps.DOCS);
            restoreBackupCompressed(LinkdbFileNames.DOCDB_FILE_NAME, linkdbStagingStorage, backupStorage);

            heartbeat.progress(BackupHeartbeatSteps.LINKS);
            restoreBackupCompressed(LinkdbFileNames.DOMAIN_LINKS_FILE_NAME, linkdbStagingStorage, backupStorage);

            heartbeat.progress(BackupHeartbeatSteps.JOURNAL);
            restoreJournal(indexStagingStorage, backupStorage);

            heartbeat.progress(BackupHeartbeatSteps.DONE);
        }
    }


    private void backupJournal(Path inputStorage, Path backupStorage) throws IOException
    {
        Optional<IndexJournal> journal = IndexJournal.findJournal(inputStorage);
        if (journal.isEmpty()) {
            throw new FileNotFoundException("No journal found in input storage");
        }

        FileUtils.copyDirectory(journal.get().journalDir().toFile(), backupStorage.resolve(journal.get().journalDir().getFileName()).toFile());
    }

    private void restoreJournal(Path destStorage, Path backupStorage) throws IOException {
        Optional<IndexJournal> journal = IndexJournal.findJournal(backupStorage);
        if (journal.isEmpty()) {
            throw new FileNotFoundException("No journal found in backup");
        }
        FileUtils.copyDirectory(backupStorage.resolve(journal.get().journalDir().getFileName()).toFile(), destStorage.toFile());
    }

    private void backupFileCompressed(String fileName, Path inputStorage, Path backupStorage) throws IOException
    {
        try (var is = Files.newInputStream(inputStorage.resolve(fileName));
             var os = new ZstdOutputStream(Files.newOutputStream(backupStorage.resolve(fileName)))
        ) {
            IOUtils.copyLarge(is, os);
        }
    }
    private void restoreBackupCompressed(String fileName, Path destStorage, Path backupStorage) throws IOException
    {
        try (var is = new ZstdInputStream(Files.newInputStream(backupStorage.resolve(fileName)));
             var os = Files.newOutputStream(destStorage.resolve(fileName))
        ) {
            IOUtils.copyLarge(is, os);
        }
    }
}
