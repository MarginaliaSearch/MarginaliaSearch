package nu.marginalia.index.client.model.domain;

import lombok.AllArgsConstructor;
import lombok.ToString;

@ToString @AllArgsConstructor
public class EdgeDomainSearchSpecification {

    public final String keyword;

    public final int queryDepth;
    public final int minHitCount;
    public final int maxResults;
}
