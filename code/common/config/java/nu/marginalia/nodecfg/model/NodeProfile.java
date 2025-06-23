package nu.marginalia.nodecfg.model;

public enum NodeProfile {
    BATCH_CRAWL,
    REALTIME,
    MIXED,
    SIDELOAD;

    public boolean isBatchCrawl() {
        return this == BATCH_CRAWL;
    }
    public boolean isRealtime() {
        return this == REALTIME;
    }
    public boolean isMixed() {
        return this == MIXED;
    }
    public boolean isSideload() {
        return this == SIDELOAD;
    }

    public boolean permitBatchCrawl() {
        return isBatchCrawl() || isMixed();
    }
    public boolean permitSideload() {  return isSideload() || isMixed(); }
}
