package nu.marginalia.index.index;

import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.reverse.ReverseIndexReader;

public class SearchIndexQueryBuilder implements IndexQueryBuilder  {
    private final IndexQuery query;
    private final ReverseIndexReader reverseIndexReader;

    SearchIndexQueryBuilder(ReverseIndexReader reverseIndexReader, IndexQuery query) {
        this.query = query;
        this.reverseIndexReader = reverseIndexReader;
    }

    public IndexQueryBuilder also(int termId) {

        query.addInclusionFilter(reverseIndexReader.also(termId));

        return this;
    }

    public IndexQueryBuilder not(int termId) {

        query.addInclusionFilter(reverseIndexReader.not(termId));

        return this;
    }

    public IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep) {

        query.addInclusionFilter(filterStep);

        return this;
    }

    public IndexQuery build() {
        return query;
        }

}
