package nu.marginalia.wmsa.edge.index.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@AllArgsConstructor @Getter
@ToString
public class EdgePutWordsRequest {
    public final EdgeId<EdgeDomain> domainId;
    public final EdgeId<EdgeUrl> urlId;
    public final double quality;

    public final EdgePageWordSet wordSet;
    private int index = 0;
}
