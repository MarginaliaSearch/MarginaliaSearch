package nu.marginalia.control.model;

import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageType;

public record FileStorageWithActions(FileStorage storage) {
    public boolean isCrawlable() {
        return storage.type() == FileStorageType.CRAWL_SPEC;
    }
    public boolean isRecrawlable() {
        return storage.type() == FileStorageType.CRAWL_DATA;
    }

    public boolean isLoadable() {
        return storage.type() == FileStorageType.PROCESSED_DATA;
    }
    public boolean isConvertible() {
        return storage.type() == FileStorageType.CRAWL_DATA;
    }
    public boolean isDeletable() {
        return storage.type() == FileStorageType.PROCESSED_DATA
             || storage.type() == FileStorageType.BACKUP;
    }
}
