package nu.marginalia.model.crawl;

public enum EdgeDomainIndexingState {
    ACTIVE("Active"),
    EXHAUSTED("Fully Crawled"),
    SPECIAL("Content is side-loaded"),
    SOCIAL_MEDIA("Social media-like website"),
    BLOCKED("Blocked"),
    REDIR("Redirected to another domain"),
    ERROR("Error during crawling"),
    UNKNOWN("Unknown");

    public String desc;

    EdgeDomainIndexingState(String desc) {
        this.desc = desc;
    }
}
