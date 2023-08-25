package nu.marginalia.control.actor;

public enum Actor {
    CRAWL,
    RECRAWL,
    CONVERT_AND_LOAD,
    CONVERTER_MONITOR,
    LOADER_MONITOR,
    CRAWLER_MONITOR,
    MESSAGE_QUEUE_MONITOR,
    PROCESS_LIVENESS_MONITOR,
    FILE_STORAGE_MONITOR,
    ADJACENCY_CALCULATION,
    CRAWL_JOB_EXTRACTOR,
    EXPORT_DATA,
    TRUNCATE_LINK_DATABASE,
    INDEX_CONSTRUCTOR_MONITOR,
    CONVERT;

    public String id() {
        return "fsm:" + name().toLowerCase();
    }
}
