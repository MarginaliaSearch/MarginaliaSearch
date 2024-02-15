package nu.marginalia.index.query;

import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.searchset.SearchSet;
import nu.marginalia.index.query.limit.SpecificationLimit;

/** IndexQueryParams is a set of parameters for a query.
 *
 * @param qualityLimit The quality limit.
 * @param year The year limit.
 * @param size The size limit.  Eliminates results from domains that do not satisfy the size criteria.
 * @param rank The rank limit.  Eliminates results from domains that do not satisfy the domain rank criteria.
 * @param domainCount The domain count limit.  Filters out results from domains that do not contain enough
 *                    documents that match the query.
 * @param searchSet The search set.  Limits the search to a set of domains.
 * @param queryStrategy The query strategy.  May impose additional constraints on the query, such as requiring
 *                      the keywords to appear in the title, or in the domain.
 */
public record IndexQueryParams(SpecificationLimit qualityLimit,
                               SpecificationLimit year,
                               SpecificationLimit size,
                               SpecificationLimit rank,
                               SpecificationLimit domainCount,
                               SearchSet searchSet,
                               QueryStrategy queryStrategy
                               )
{

}
