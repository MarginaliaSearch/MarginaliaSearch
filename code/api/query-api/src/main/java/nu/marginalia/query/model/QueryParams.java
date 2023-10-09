package nu.marginalia.query.model;

import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.SpecificationLimit;

import java.util.List;

public record QueryParams(
        String humanQuery,
        String nearDomain,
        List<String> tacitIncludes,
        List<String> tacitExcludes,
        List<String> tacitPriority,
        List<String> tacitAdvice,
        SpecificationLimit quality,
        SpecificationLimit year,
        SpecificationLimit size,
        SpecificationLimit rank,
        List<Integer> domainIds,
        QueryLimits limits,
        SearchSetIdentifier identifier
)
{
}
