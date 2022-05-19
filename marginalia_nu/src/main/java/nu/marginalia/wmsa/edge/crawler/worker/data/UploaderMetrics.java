package nu.marginalia.wmsa.edge.crawler.worker.data;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor @AllArgsConstructor @With
public class UploaderMetrics {
    public long pagesIndexed = 0L;
    public long domainsIndexed = 0L;
    public long extLinksDiscovered = 0L;
    public long duds = 0L;
    public long waitTime = 0L;
    public long aliasedDomains = 0L;
}
