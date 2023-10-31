package nu.marginalia.storage.model;

import java.util.EnumSet;

public enum FileStorageBaseType {
    CURRENT,
    WORK,
    STORAGE,
    BACKUP;

    public boolean permitsStorageType(FileStorageType type) {
        return switch (this) {
            case BACKUP -> FileStorageType.BACKUP.equals(type);
            case STORAGE -> EnumSet.of(FileStorageType.EXPORT, FileStorageType.CRAWL_DATA, FileStorageType.PROCESSED_DATA, FileStorageType.CRAWL_SPEC).contains(type);
            default -> false;
        };
    }
}
