package nu.marginalia.storage.model;

public enum FileStorageType {
    CRAWL_SPEC,
    CRAWL_DATA,
    PROCESSED_DATA,
    BACKUP,
    EXPORT;
    public String overrideName() {
        return "FS_OVERRIDE:"+name();
    }
}
