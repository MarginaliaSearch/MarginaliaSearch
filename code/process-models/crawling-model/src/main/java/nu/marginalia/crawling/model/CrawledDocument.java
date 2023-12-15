package nu.marginalia.crawling.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import nu.marginalia.bigstring.BigString;
import nu.marginalia.model.EdgeUrl;

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
    public String documentBody;
    public String documentBodyHash;

    public String canonicalUrl;
    public String redirectUrl;

    public String recrawlState;

    /** This is not guaranteed to be set in all versions of the format,
     * information may come in CrawledDomain instead */
    public Boolean hasCookies = false;

    public static final String SERIAL_IDENTIFIER = "// DOCUMENT";
    @Override
    public String getSerialIdentifier() {
        return SERIAL_IDENTIFIER;
    }

    @Override
    public String getDomain() {
        if (url == null)
            return null;

        return EdgeUrl
                .parse(url)
                .map(EdgeUrl::getDomain)
                .map(d -> d.domain)
                .orElse(null);
    }

}
