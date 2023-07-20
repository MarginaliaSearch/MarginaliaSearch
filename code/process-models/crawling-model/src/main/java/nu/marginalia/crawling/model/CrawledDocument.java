package nu.marginalia.crawling.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import nu.marginalia.bigstring.BigString;

@Builder
@AllArgsConstructor
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

    public String recrawlState;

    public static final String SERIAL_IDENTIFIER = "// DOCUMENT";
    @Override
    public String getSerialIdentifier() {
        return SERIAL_IDENTIFIER;
    }

    /** Remove all large data from this object to save memory */
    public void dispose() {
        documentBody = null;
        headers = null;
    }
}
