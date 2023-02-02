package nu.marginalia.wmsa.edge.model.search;

import lombok.*;
import nu.marginalia.wmsa.edge.index.model.QueryLimits;
import nu.marginalia.wmsa.edge.index.model.QueryStrategy;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSetIdentifier;
import nu.marginalia.wmsa.edge.model.search.domain.SpecificationLimit;

import java.util.List;

@ToString @Getter @Builder @With @AllArgsConstructor
public class EdgeSearchSpecification {
    public List<EdgeSearchSubquery> subqueries;
    public List<Integer> domains;
    public SearchSetIdentifier searchSetIdentifier;

    public final String humanQuery;

    public final SpecificationLimit quality;
    public final SpecificationLimit year;
    public final SpecificationLimit size;

    public final QueryLimits queryLimits;
    public final QueryStrategy queryStrategy;

}
