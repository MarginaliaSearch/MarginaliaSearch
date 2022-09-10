package nu.marginalia.wmsa.edge.index.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.id.EdgeId;

@AllArgsConstructor @Getter
@ToString
public class EdgePutWordsRequest {
    public EdgeId<EdgeDomain> domainId;
    public EdgeId<EdgeUrl> urlId;
    public double quality;

    public EdgePageWordSet wordSet;
    private int index = 0;
}
