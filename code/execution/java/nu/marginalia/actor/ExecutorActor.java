package nu.marginalia.actor;

public enum ExecutorActor {
    CRAWL,
    RECRAWL,
    RECRAWL_SINGLE_DOMAIN,
    CONVERT_AND_LOAD,
    PROC_CONVERTER_SPAWNER,
    PROC_LOADER_SPAWNER,
    PROC_CRAWLER_SPAWNER,
    MONITOR_PROCESS_LIVENESS,
    MONITOR_FILE_STORAGE,
    ADJACENCY_CALCULATION,
    CRAWL_JOB_EXTRACTOR,
    EXPORT_DATA,
    EXPORT_SEGMENTATION_MODEL,
    EXPORT_ATAGS,
    EXPORT_TERM_FREQUENCIES,
    EXPORT_FEEDS,
    PROC_INDEX_CONSTRUCTOR_SPAWNER,
    CONVERT,
    RESTORE_BACKUP,
    EXPORT_SAMPLE_DATA,
    DOWNLOAD_SAMPLE,
    SCRAPE_FEEDS;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }

    public String id(int node) {
        return "fsm:" + name().toLowerCase() + ":" + node;
    }

}
