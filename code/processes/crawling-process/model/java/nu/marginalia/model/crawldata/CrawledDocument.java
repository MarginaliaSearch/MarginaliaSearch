package nu.marginalia.model.crawldata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import nu.marginalia.model.EdgeUrl;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

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

    @Nullable
    public String headers;

    public String documentBody;

    @Deprecated
    public String documentBodyHash;

    @Deprecated
    public String canonicalUrl;
    public String redirectUrl;

    @Deprecated
    public String recrawlState;

    /** This is not guaranteed to be set in all versions of the format,
     * information may come in CrawledDomain instead */
    public Boolean hasCookies = false;

    public String lastModifiedMaybe;
    public String etagMaybe;

    @Nullable
    private String getHeader(String header) {
        if (headers == null) {
            return null;
        }

        String headerString = header + ":";

        String[] headersLines = StringUtils.split(headers, '\n');
        for (String headerLine : headersLines) {
            if (StringUtils.startsWithIgnoreCase(headerLine, headerString)) {
                return headerLine.substring(headerString.length()).trim();
            }
        }

        return null;
    }

    /** Returns the ETag header, or null if not present;
     * <p>
     * this is a compatibility shim between the old json format, which saves headers in a long string
     * and the new parquet format which saves only the ETag and Last-Modified headers in separate columns
     * */
    public String getEtag() {
        if (etagMaybe != null) {
            return etagMaybe;
        }
        return getHeader("ETag");
    }

    /** Returns the Last-Modified header, or null if not present
     * <p>
     * this is a compatibility shim between the old json format, which saves headers in a long string
     *      * and the new parquet format which saves only the ETag and Last-Modified headers in separate columns
     * */
    public String getLastModified() {
        if (lastModifiedMaybe != null) {
            return lastModifiedMaybe;
        }
        return getHeader("Last-Modified");
    }

    @Override
    public String getDomain() {
        if (url == null)
            return null;

        return EdgeUrl
                .parse(url)
                .map(EdgeUrl::getDomain)
                .map(Object::toString)
                .orElse(null);
    }

}
