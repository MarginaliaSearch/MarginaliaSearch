package nu.marginalia.index.query;

import nu.marginalia.index.query.filter.QueryFilterStepIf;

/** Builds a query.
 * <p />
 * Note: The query builder may omit predicates that are deemed redundant.
 */
public interface IndexQueryBuilder {
    /** Filters documents that also contain termId, within the full index.
     */
    IndexQueryBuilder alsoFull(int termId);

    /**
     * Filters documents that also contain the termId, within the priority index.
     */
    IndexQueryBuilder alsoPrio(int termIds);

    /** Excludes documents that contain termId, within the full index
     */
    IndexQueryBuilder notFull(int termId);

    IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep);

    IndexQuery build();
}
