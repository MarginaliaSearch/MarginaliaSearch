package nu.marginalia.svc;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import nu.marginalia.IndexLocations;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.apache.commons.io.IOUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class BackupService {

    private final FileStorageService storageService;

    @Inject
    public BackupService(FileStorageService storageService) {
        this.storageService = storageService;
    }

    /** Create a new backup of the contents in the _STAGING storage areas.
     * This backup can later be dehydrated and quickly loaded into _LIVE.
     * */
    public void createBackupFromStaging(List<FileStorageId> associatedIds) throws SQLException, IOException {
        var backupBase = storageService.getStorageBase(FileStorageBaseType.BACKUP);

        String desc = "Pre-load backup snapshot " + LocalDateTime.now();

        var backupStorage = storageService.allocateTemporaryStorage(backupBase,
                FileStorageType.BACKUP, "snapshot", desc);

        for (var associatedId : associatedIds) {
            storageService.relateFileStorages(associatedId, backupStorage.id());
        }


        var indexStagingStorage = IndexLocations.getIndexConstructionArea(storageService);
        var linkdbStagingStorage = IndexLocations.getLinkdbWritePath(storageService);

        backupFileCompressed("links.db", linkdbStagingStorage, backupStorage.asPath());
        // This file format is already compressed
        backupJournal(indexStagingStorage, backupStorage.asPath());
    }


    /** Read back a backup into _STAGING */
    public void restoreBackup(FileStorageId backupId) throws SQLException, IOException {
        var backupStorage = storageService.getStorage(backupId).asPath();

        var indexStagingStorage = IndexLocations.getIndexConstructionArea(storageService);
        var linkdbStagingStorage = IndexLocations.getLinkdbWritePath(storageService);

        restoreBackupCompressed("links.db", linkdbStagingStorage, backupStorage);
        restoreJournal(indexStagingStorage, backupStorage);
    }


    private void backupJournal(Path inputStorage, Path backupStorage) throws IOException
    {
        for (var source : IndexJournalFileNames.findJournalFiles(inputStorage)) {
            var dest = backupStorage.resolve(source.toFile().getName());

            try (var is = Files.newInputStream(source);
                 var os = Files.newOutputStream(dest)
            ) {
                IOUtils.copyLarge(is, os);
            }
        }

    }

    private void restoreJournal(Path destStorage, Path backupStorage) throws IOException {

        // Remove any old journal files first to avoid them getting loaded
        for (var garbage : IndexJournalFileNames.findJournalFiles(destStorage)) {
            Files.delete(garbage);
        }

        for (var source : IndexJournalFileNames.findJournalFiles(backupStorage)) {
            var dest = destStorage.resolve(source.toFile().getName());

            try (var is = Files.newInputStream(source);
                 var os = Files.newOutputStream(dest)
            ) {
                IOUtils.copyLarge(is, os);
            }
        }

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
