package nu.marginalia.wmsa.edge.model.search.domain;

import lombok.AllArgsConstructor;
import lombok.ToString;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

@ToString @AllArgsConstructor
public class EdgeDomainSearchSpecification {
    public final int bucket;
    public final IndexBlock block;
    public final String keyword;
    public final int queryDepth;
    public final int minHitCount;
    public final int maxResults;
}
