package nu.marginalia.actor;

import nu.marginalia.nodecfg.model.NodeProfile;

import java.util.Set;

public enum ExecutorActor {
    PREC_EXPORT_ALL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    UPDATE_NSFW_LISTS(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.REALTIME, NodeProfile.WIDE_DOMAINS),

    CRAWL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    RECRAWL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    RECRAWL_SINGLE_DOMAIN(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    PROC_CRAWLER_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    PROC_PING_SPAWNER(NodeProfile.REALTIME),
    PROC_EXPORT_TASKS_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    PROC_NDP_SPAWNER(NodeProfile.MIXED, NodeProfile.REALTIME),
    NDP(NodeProfile.MIXED, NodeProfile.REALTIME),
    ADJACENCY_CALCULATION(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    EXPORT_DATA(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    EXPORT_SEGMENTATION_MODEL(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    EXPORT_ATAGS(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    EXPORT_TERM_FREQUENCIES(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    EXPORT_FEEDS(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    EXPORT_SAMPLE_DATA(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    EXPORT_DOM_SAMPLE_DATA(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    DOWNLOAD_SAMPLE(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),
    SCHEDULED_MAINTENANCE(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.WIDE_DOMAINS),

    MIGRATE_DOMAINS(NodeProfile.WIDE_DOMAINS),
    CLEANUP_MIGRATED_DOMAINS(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED),
    WIDE_CRAWL(NodeProfile.WIDE_DOMAINS),

    PROC_CONVERTER_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),
    PROC_LOADER_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),
    RESTORE_BACKUP(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),
    CONVERT(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),

    CONVERT_AND_LOAD(NodeProfile.BATCH_CRAWL, NodeProfile.MIXED, NodeProfile.REALTIME, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),
    MONITOR_PROCESS_LIVENESS(NodeProfile.BATCH_CRAWL, NodeProfile.REALTIME, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),
    MONITOR_FILE_STORAGE(NodeProfile.BATCH_CRAWL, NodeProfile.REALTIME, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),
    PROC_INDEX_CONSTRUCTOR_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.REALTIME, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),
    PROC_RANKING_CONSTRUCTOR_SPAWNER(NodeProfile.BATCH_CRAWL, NodeProfile.REALTIME, NodeProfile.MIXED, NodeProfile.SIDELOAD, NodeProfile.WIDE_DOMAINS),

    LIVE_CRAWL(NodeProfile.REALTIME),
    PROC_LIVE_CRAWL_SPAWNER(NodeProfile.REALTIME),
    SCRAPE_FEEDS(NodeProfile.REALTIME),
    UPDATE_RSS(NodeProfile.REALTIME),
    DOM_SAMPLE_ACTOR(NodeProfile.REALTIME),
    SCREENSHOT_ACTOR(NodeProfile.REALTIME),
    ;

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
