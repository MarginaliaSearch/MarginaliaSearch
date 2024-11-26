package nu.marginalia.actor;

import nu.marginalia.nodecfg.model.NodeProfile;

import java.util.Set;

public enum ExecutorActor {
    PREC_EXPORT_ALL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),

    CRAWL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    RECRAWL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    RECRAWL_SINGLE_DOMAIN(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    PROC_CRAWLER_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    PROC_EXPORT_TASKS_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    ADJACENCY_CALCULATION(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    EXPORT_DATA(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    EXPORT_SEGMENTATION_MODEL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    EXPORT_ATAGS(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    EXPORT_TERM_FREQUENCIES(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    EXPORT_FEEDS(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    EXPORT_SAMPLE_DATA(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    DOWNLOAD_SAMPLE(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),

    PROC_CONVERTER_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD),
    PROC_LOADER_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD),
    RESTORE_BACKUP(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD),
    CONVERT(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD),

    CONVERT_AND_LOAD(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.REALTIME, NodeProfile.SIDELOAD),
    MONITOR_PROCESS_LIVENESS(NodeProfile.BATCH_CRAWL, NodeProfile.REALTIME, NodeProfile.MIXED, NodeProfile.SIDELOAD),
    MONITOR_FILE_STORAGE(NodeProfile.BATCH_CRAWL, NodeProfile.REALTIME, NodeProfile.MIXED, NodeProfile.SIDELOAD),
    PROC_INDEX_CONSTRUCTOR_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.REALTIME, NodeProfile.MIXED, NodeProfile.SIDELOAD),

    LIVE_CRAWL(NodeProfile.REALTIME),
    PROC_LIVE_CRAWL_SPAWNER(NodeProfile.REALTIME),
    SCRAPE_FEEDS(NodeProfile.REALTIME),
    UPDATE_RSS(NodeProfile.REALTIME);

    public String id() {
        return "fsm:" + name().toLowerCase();
    }

    public String id(int node) {
        return "fsm:" + name().toLowerCase() + ":" + node;
    }

    ExecutorActor(NodeProfile... profileSet) {
        this.profileSet = Set.of(profileSet);
    }

    public Set<NodeProfile> profileSet;
}
