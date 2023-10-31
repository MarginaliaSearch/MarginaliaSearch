package nu.marginalia.actor;

public enum ExecutorActor {
    CRAWL,
    RECRAWL,
    CONVERT_AND_LOAD,
    PROC_CONVERTER_SPAWNER,
    PROC_LOADER_SPAWNER,
    PROC_CRAWLER_SPAWNER,
    MONITOR_PROCESS_LIVENESS,
    MONITOR_FILE_STORAGE,
    ADJACENCY_CALCULATION,
    CRAWL_JOB_EXTRACTOR,
    EXPORT_DATA,
    EXPORT_ATAGS,
    PROC_INDEX_CONSTRUCTOR_SPAWNER,
    CONVERT,
    RESTORE_BACKUP;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
