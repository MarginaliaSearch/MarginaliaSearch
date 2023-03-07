package nu.marginalia.index.query;

import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.query.limit.SpecificationLimit;

public record IndexQueryParams(SpecificationLimit qualityLimit,
                               SpecificationLimit year,
                               SpecificationLimit size,
                               SpecificationLimit rank,
                               SearchSet searchSet,
                               QueryStrategy queryStrategy
                               )
{

}
