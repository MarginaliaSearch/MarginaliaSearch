package nu.marginalia.db.storage.model;

public enum FileStorageType {
    CRAWL_SPEC,
    CRAWL_DATA,
    PROCESSED_DATA,
    INDEX_STAGING,
    LEXICON_STAGING,
    LINKDB_STAGING,
    LINKDB_LIVE,
    INDEX_LIVE,
    LEXICON_LIVE,
    BACKUP,
    EXPORT,
    SEARCH_SETS
}
