package nu.marginalia.wmsa.edge.crawling.model;

import lombok.Builder;

@Builder
public class CrawledDocument implements SerializableCrawlData {
    public String crawlId;

    public String url;
    public String contentType;

    public String timestamp;
    public int httpStatus;

    public String crawlerStatus;
    public String crawlerStatusDesc;

    public String headers;
    public String documentBody;

    public String documentBodyHash;

    public String canonicalUrl;
    public String redirectUrl;

    public static final String SERIAL_IDENTIFIER = "// DOCUMENT";
    @Override
    public String getSerialIdentifier() {
        return SERIAL_IDENTIFIER;
    }
}
