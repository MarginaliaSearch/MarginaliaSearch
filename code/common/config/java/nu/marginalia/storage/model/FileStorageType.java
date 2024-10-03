package nu.marginalia.storage.model;

public enum FileStorageType {
    @Deprecated
    CRAWL_SPEC, //

    CRAWL_DATA,
    PROCESSED_DATA,
    BACKUP,
    EXPORT;
}
