package nu.marginalia.control.svc;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageId;
import nu.marginalia.db.storage.model.FileStorageType;
import org.apache.commons.io.IOUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class BackupService {

    private final FileStorageService storageService;

    @Inject
    public BackupService(FileStorageService storageService) {
        this.storageService = storageService;
    }

    /** Create a new backup of the contents in the _STAGING storage areas.
     * This backup can later be dehydrated and quickly loaded into _LIVE.
     * */
    public void createBackupFromStaging(FileStorageId associatedId) throws SQLException, IOException {
        var backupBase = storageService.getStorageBase(FileStorageBaseType.BACKUP);

        String desc = "Pre-load backup snapshot " + LocalDateTime.now();

        var backupStorage = storageService.allocateTemporaryStorage(backupBase, FileStorageType.BACKUP, "snapshot", desc);

        storageService.relateFileStorages(associatedId, backupStorage.id());

        var indexStagingStorage = storageService.getStorageByType(FileStorageType.INDEX_STAGING);
        var linkdbStagingStorage = storageService.getStorageByType(FileStorageType.LINKDB_STAGING);
        var lexiconStagingStorage = storageService.getStorageByType(FileStorageType.LEXICON_STAGING);

        backupFileCompressed("links.db", linkdbStagingStorage, backupStorage);
        backupFileCompressed("dictionary.dat", lexiconStagingStorage, backupStorage);
        // This file format is already compressed
        backupFileNoCompression("page-index.dat", indexStagingStorage, backupStorage);
    }


    /** Read back a backup into _STAGING */
    public void restoreBackup(FileStorageId backupId) throws SQLException, IOException {
        var backupStorage = storageService.getStorage(backupId);

        var indexStagingStorage = storageService.getStorageByType(FileStorageType.INDEX_STAGING);
        var linkdbStagingStorage = storageService.getStorageByType(FileStorageType.LINKDB_STAGING);
        var lexiconStagingStorage = storageService.getStorageByType(FileStorageType.LEXICON_STAGING);

        restoreBackupCompressed("links.db", linkdbStagingStorage, backupStorage);
        restoreBackupCompressed("dictionary.dat", lexiconStagingStorage, backupStorage);
        restoreBackupNoCompression("page-index.dat", indexStagingStorage, backupStorage);
    }


    private void backupFileNoCompression(String fileName, FileStorage inputStorage, FileStorage backupStorage) throws IOException
    {
        try (var is = Files.newInputStream(inputStorage.asPath().resolve(fileName));
             var os = Files.newOutputStream(backupStorage.asPath().resolve(fileName))
        ) {
            IOUtils.copyLarge(is, os);
        }
    }

    private void restoreBackupNoCompression(String fileName, FileStorage inputStorage, FileStorage backupStorage) throws IOException {
        try (var is = Files.newInputStream(backupStorage.asPath().resolve(fileName));
             var os = Files.newOutputStream(inputStorage.asPath().resolve(fileName))
        ) {
            IOUtils.copyLarge(is, os);
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
    private void restoreBackupCompressed(String fileName, FileStorage inputStorage, FileStorage backupStorage) throws IOException
    {
        try (var is = new ZstdInputStream(Files.newInputStream(backupStorage.asPath().resolve(fileName)));
             var os = Files.newOutputStream(backupStorage.asPath().resolve(fileName))
        ) {
            IOUtils.copyLarge(is, os);
        }
    }
}
