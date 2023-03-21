package nu.marginalia.index.index;

import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.full.ReverseIndexFullReader;

public class SearchIndexQueryBuilder implements IndexQueryBuilder  {
    private final IndexQuery query;
    private final ReverseIndexFullReader reverseIndexFullReader;

    SearchIndexQueryBuilder(ReverseIndexFullReader reverseIndexFullReader, IndexQuery query) {
        this.query = query;
        this.reverseIndexFullReader = reverseIndexFullReader;
    }

    public IndexQueryBuilder also(int termId) {

        query.addInclusionFilter(reverseIndexFullReader.also(termId));

        return this;
    }

    public IndexQueryBuilder not(int termId) {

        query.addInclusionFilter(reverseIndexFullReader.not(termId));

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
