package nu.marginalia.wmsa.edge.model.crawl;

import lombok.Data;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.Map;
import java.util.Set;

@Data
public class EdgePageContent {
    public final EdgeUrl url;
    public final EdgePageWords words;
    public final Map<EdgeUrl, Set<String>> linkWords;
    public final EdgePageMetadata metadata;
    public final int hash;
    public final String ipAddress;

    public boolean hasHotLink(EdgeUrl url) {
        return linkWords.containsKey(url);
    }

    public int numWords() {
        return metadata.totalWords;
    }
}
