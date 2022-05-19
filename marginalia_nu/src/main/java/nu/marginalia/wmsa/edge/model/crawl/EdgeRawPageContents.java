package nu.marginalia.wmsa.edge.model.crawl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@Data @Getter @AllArgsConstructor
public class EdgeRawPageContents {
    public final EdgeUrl url;
    public final EdgeUrl redirectUrl;
    public final String data;
    public final EdgeContentType contentType;
    public final String ip;
    public boolean hasCookies;
    public final String fetchTimestamp;

    public boolean isAfter(String dateIso8601) {
        if (fetchTimestamp == null) {
            return false;
        }
        return fetchTimestamp.compareTo(dateIso8601) >= 0;
    }
}
