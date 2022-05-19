package nu.marginalia.wmsa.edge.crawling.model;

import java.util.List;

public class CrawlingSpecification {
    public String id;

    public int crawlDepth;

    // Don't make this EdgeUrl, EdgeDomain etc. -- we want this plastic to change!
    public String domain;
    public List<String> urls;
}
