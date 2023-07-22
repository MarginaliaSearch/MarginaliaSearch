package nu.marginalia.crawling.model.spec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.With;
import nu.marginalia.crawling.model.CrawledDomain;

import java.util.List;

@AllArgsConstructor @NoArgsConstructor @Builder @With
public class CrawlingSpecification {
    public String id;

    public int crawlDepth;

    // Don't make this EdgeUrl, EdgeDomain etc. -- we want this plastic to change!
    public String domain;
    public List<String> urls;

    @Override
    public String toString() {
        return String.format(getClass().getSimpleName() + "[" + id + "/" + domain + ": " + crawlDepth + "[ " + urls.size() + "]");
    }
}
