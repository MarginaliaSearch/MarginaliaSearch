package nu.marginalia.control.svc;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginallia.index.journal.IndexJournalFileNames;
import org.apache.commons.io.IOUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
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

        var backupStorage = storageService.allocateTemporaryStorage(backupBase, FileStorageType.BACKUP, "snapshot", desc);

        for (var associatedId : associatedIds) {
            storageService.relateFileStorages(associatedId, backupStorage.id());
        }

        var indexStagingStorage = storageService.getStorageByType(FileStorageType.INDEX_STAGING);
        var linkdbStagingStorage = storageService.getStorageByType(FileStorageType.LINKDB_STAGING);

        backupFileCompressed("links.db", linkdbStagingStorage, backupStorage);
        // This file format is already compressed
        backupJournal(indexStagingStorage, backupStorage);
    }


    /** Read back a backup into _STAGING */
    public void restoreBackup(FileStorageId backupId) throws SQLException, IOException {
        var backupStorage = storageService.getStorage(backupId);

        var indexStagingStorage = storageService.getStorageByType(FileStorageType.INDEX_STAGING);
        var linkdbStagingStorage = storageService.getStorageByType(FileStorageType.LINKDB_STAGING);

        restoreBackupCompressed("links.db", linkdbStagingStorage, backupStorage);
        restoreJournal(indexStagingStorage, backupStorage);
    }


    private void backupJournal(FileStorage inputStorage, FileStorage backupStorage) throws IOException
    {
        for (var source : IndexJournalFileNames.findJournalFiles(inputStorage.asPath())) {
            var dest = backupStorage.asPath().resolve(source.toFile().getName());

            try (var is = Files.newInputStream(source);
                 var os = Files.newOutputStream(dest)
            ) {
                IOUtils.copyLarge(is, os);
            }
        }

    }

    private void restoreJournal(FileStorage destStorage, FileStorage backupStorage) throws IOException {

        // Remove any old journal files first to avoid them getting loaded
        for (var garbage : IndexJournalFileNames.findJournalFiles(destStorage.asPath())) {
            Files.delete(garbage);
        }

        for (var source : IndexJournalFileNames.findJournalFiles(backupStorage.asPath())) {
            var dest = destStorage.asPath().resolve(source.toFile().getName());

            try (var is = Files.newInputStream(source);
                 var os = Files.newOutputStream(dest)
            ) {
                IOUtils.copyLarge(is, os);
            }
        }

    }

    private void backupFileCompressed(String fileName, FileStorage inputStorage, FileStorage backupStorage) throws IOException
    {
        try (var is = Files.newInputStream(inputStorage.asPath().resolve(fileName));
             var os = new ZstdOutputStream(Files.newOutputStream(backupStorage.asPath().resolve(fileName)))
        ) {
            IOUtils.copyLarge(is, os);
        }
    }
    private void restoreBackupCompressed(String fileName, FileStorage destStorage, FileStorage backupStorage) throws IOException
    {
        try (var is = new ZstdInputStream(Files.newInputStream(backupStorage.asPath().resolve(fileName)));
             var os = Files.newOutputStream(destStorage.asPath().resolve(fileName))
        ) {
            IOUtils.copyLarge(is, os);
        }
    }
}
