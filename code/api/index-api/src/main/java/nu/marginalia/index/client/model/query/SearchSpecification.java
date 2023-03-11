package nu.marginalia.index.client.model.query;

import lombok.*;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;

import java.util.List;

@ToString @Getter @Builder @With @AllArgsConstructor
public class SearchSpecification {
    public List<SearchSubquery> subqueries;

    /** If present and not empty, limit the search to these domain IDs */
    public List<Integer> domains;

    public SearchSetIdentifier searchSetIdentifier;

    public final String humanQuery;

    public final SpecificationLimit quality;
    public final SpecificationLimit year;
    public final SpecificationLimit size;
    public final SpecificationLimit rank;

    public final QueryLimits queryLimits;

    public final QueryStrategy queryStrategy;

}
