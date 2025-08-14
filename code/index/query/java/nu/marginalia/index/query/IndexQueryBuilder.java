package nu.marginalia.index.query;

import nu.marginalia.index.query.filter.QueryFilterStepIf;

/** Builds a query.
 * <p />
 * Note: The query builder may omit predicates that are deemed redundant.
 */
public interface IndexQueryBuilder {
    /** Filters documents that also contain termId, within the full index.
     */
    IndexQueryBuilder also(long termId, IndexSearchBudget budget);

    /** Excludes documents that contain termId, within the full index
     */
    IndexQueryBuilder not(long termId, IndexSearchBudget budget);

    IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep);

    IndexQuery build();
}
