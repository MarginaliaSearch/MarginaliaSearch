package nu.marginalia.wmsa.edge.crawling.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@AllArgsConstructor @Data @Builder
public class CrawledDomain {
    public String id;
    public String domain;

    public String redirectDomain;

    public String crawlerStatus;
    public String crawlerStatusDesc;
    public String ip;

    public List<CrawledDocument> doc;
    public List<String> cookies;

    public int size() {
        if (doc == null) return 0;
        return doc.size();
    }
}
