package nu.marginalia.wmsa.edge.model.search;

import lombok.*;

import java.util.List;

@ToString @Getter @Builder @With @AllArgsConstructor
public class EdgeSearchSpecification {

    public List<Integer> buckets;
    public List<EdgeSearchSubquery> subqueries;
    public final int limitByDomain;
    public final int limitTotal;

    public final String humanQuery;

    public final int timeoutMs;
    public final int fetchSize;

}
