package nu.marginalia.crawling.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@AllArgsConstructor @Data @Builder
public class CrawledDomain implements SerializableCrawlData {
    public String domain;

    public String redirectDomain;

    public String crawlerStatus;
    public String crawlerStatusDesc;
    public String ip;

    public List<CrawledDocument> doc;

    /** This is not guaranteed to be set in all versions of the format,
     * information may come in CrawledDocument instead */
    public List<String> cookies;

    public int size() {
        if (doc == null) return 0;
        return doc.size();
    }

}
