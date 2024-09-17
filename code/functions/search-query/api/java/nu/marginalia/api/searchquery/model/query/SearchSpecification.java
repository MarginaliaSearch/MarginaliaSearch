package nu.marginalia.api.searchquery.model.query;

import lombok.*;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;

import java.util.List;

@ToString @Getter @Builder @With @AllArgsConstructor
public class SearchSpecification {
    public SearchQuery query;

    /** If present and not empty, limit the search to these domain IDs */
    public List<Integer> domains;

    public String searchSetIdentifier;

    public final String humanQuery;

    @Builder.Default
    public final SpecificationLimit quality = SpecificationLimit.none();
    @Builder.Default
    public final SpecificationLimit year = SpecificationLimit.none();
    @Builder.Default
    public final SpecificationLimit size = SpecificationLimit.none();
    @Builder.Default
    public final SpecificationLimit rank = SpecificationLimit.none();

    public final QueryLimits queryLimits;

    public final QueryStrategy queryStrategy;

    public final ResultRankingParameters rankingParams;
}
