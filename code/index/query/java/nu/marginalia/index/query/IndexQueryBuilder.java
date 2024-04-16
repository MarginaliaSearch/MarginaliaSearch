package nu.marginalia.index.query;

import nu.marginalia.index.query.filter.QueryFilterStepIf;

import java.util.List;

/** Builds a query.
 * <p />
 * Note: The query builder may omit predicates that are deemed redundant.
 */
public interface IndexQueryBuilder {
    /** Filters documents that also contain termId, within the full index.
     */
    IndexQueryBuilder also(long termId);

    /** Excludes documents that contain termId, within the full index
     */
    IndexQueryBuilder not(long termId);

    IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep);
    IndexQueryBuilder addInclusionFilterAny(List<QueryFilterStepIf> filterStep);

    IndexQuery build();
}
