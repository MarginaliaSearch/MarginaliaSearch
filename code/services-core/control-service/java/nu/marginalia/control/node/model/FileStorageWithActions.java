package nu.marginalia.control.node.model;

import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record FileStorageWithActions(FileStorage storage) {
    public boolean isStatusNew() {
        return storage.state() == FileStorageState.NEW;
    }
    public boolean isAtagsExportable() {
        return storage.type() == FileStorageType.CRAWL_DATA;
    }
    public boolean isRecrawlable() {
        return storage.type() == FileStorageType.CRAWL_DATA;
    }

    public boolean isLoadable() {
        return storage.type() == FileStorageType.PROCESSED_DATA;
    }
    public boolean isRestorable() {
        return storage.type() == FileStorageType.BACKUP;
    }
    public boolean isConvertible() {
        return storage.type() == FileStorageType.CRAWL_DATA;
    }
    public boolean isDeletable() {
        var baseType = storage.base().type();

        return baseType == FileStorageBaseType.STORAGE
            || baseType == FileStorageBaseType.BACKUP;
    }

    public String getRelPath() {
        Path basePath = storage.base().asPath();
        Path storagePath = storage.asPath();

        return basePath.relativize(storagePath)
                .toString();
    }

    public String getTimestampFull() {
        return storage.createDateTime().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public String getTimestamp() {
        var ctime = storage.createDateTime();
        if (ctime.isAfter(LocalDate.now().atStartOfDay())) {
            return storage.createDateTime().format(DateTimeFormatter.ISO_TIME);
        }
        else {
            return storage.createDateTime().format(DateTimeFormatter.ISO_DATE);
        }

    }
}
