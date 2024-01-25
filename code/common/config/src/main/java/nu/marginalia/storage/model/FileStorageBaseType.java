package nu.marginalia.storage.model;

public enum FileStorageBaseType {
    CURRENT,
    WORK,
    STORAGE,
    BACKUP;


    public static FileStorageBaseType forFileStorageType(FileStorageType type) {
        return switch (type) {
            case EXPORT, CRAWL_DATA, PROCESSED_DATA, CRAWL_SPEC -> STORAGE;
            case BACKUP -> BACKUP;
        };
    }

}
