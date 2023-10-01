package nu.marginalia.db.storage.model;

public enum FileStorageType {
    CRAWL_SPEC,
    CRAWL_DATA,
    PROCESSED_DATA,
    INDEX_STAGING,
    LINKDB_STAGING,
    LINKDB_LIVE,
    INDEX_LIVE,
    BACKUP,
    EXPORT,
    SEARCH_SETS;

    public String overrideName() {
        return "FS_OVERRIDE:"+name();
    }
}
