package nu.marginalia.index.query;

import nu.marginalia.index.query.filter.QueryFilterStepIf;

public interface IndexQueryBuilder {
    IndexQueryBuilder also(int termId);

    IndexQueryBuilder not(int termId);
    IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep);

    IndexQuery build();
}
