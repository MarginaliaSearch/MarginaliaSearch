package nu.marginalia.wmsa.edge.crawling.model;

import lombok.Builder;
import lombok.ToString;
import nu.marginalia.util.bigstring.BigString;
import nu.marginalia.util.bigstring.CompressedBigString;

@Builder
@ToString
public class CrawledDocument implements SerializableCrawlData {
    public String crawlId;

    public String url;
    public String contentType;

    public String timestamp;
    public int httpStatus;

    public String crawlerStatus;
    public String crawlerStatusDesc;

    public String headers;
    public BigString documentBody;
    public String documentBodyHash;

    public String canonicalUrl;
    public String redirectUrl;

    public static final String SERIAL_IDENTIFIER = "// DOCUMENT";
    @Override
    public String getSerialIdentifier() {
        return SERIAL_IDENTIFIER;
    }
}
