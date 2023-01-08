package nu.marginalia.wmsa.edge.index.query;

import nu.marginalia.wmsa.edge.index.model.QueryStrategy;
import nu.marginalia.wmsa.edge.index.svc.searchset.SearchSet;
import nu.marginalia.wmsa.edge.model.search.domain.SpecificationLimit;

public record IndexQueryParams(SpecificationLimit qualityLimit,
                               SpecificationLimit year,
                               SpecificationLimit size,
                               SearchSet searchSet,
                               QueryStrategy queryStrategy
                               )
{

}
