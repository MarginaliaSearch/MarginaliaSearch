package nu.marginalia.index.index;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.index.ReverseIndexReader;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.filter.QueryFilterStepIf;

public class IndexQueryBuilderImpl implements IndexQueryBuilder  {
    private final IndexQuery query;
    private final ReverseIndexReader reverseIndexFullReader;
    private final ReverseIndexReader reverseIndexPrioReader;

    /* Keep track of already added include terms to avoid redundant checks.
     *
     * Warning: This may cause unexpected behavior if for example attempting to
     * first check one index and then another for the same term. At the moment, that
     * makes no sense, but in the future, that might be a thing one might want to do.
     * */
    private final TLongHashSet alreadyConsideredTerms = new TLongHashSet();

    IndexQueryBuilderImpl(ReverseIndexReader reverseIndexFullReader,
                          ReverseIndexReader reverseIndexPrioReader,
                          IndexQuery query)
    {
        this.query = query;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPrioReader = reverseIndexPrioReader;
    }

    public IndexQueryBuilder withSourceTerms(long... sourceTerms) {
        alreadyConsideredTerms.addAll(sourceTerms);

        return this;
    }

    public IndexQueryBuilder alsoFull(long termId) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(reverseIndexFullReader.also(termId));
        }

        return this;
    }

    public IndexQueryBuilder alsoPrio(long termId) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(reverseIndexPrioReader.also(termId));
        }

        return this;
    }

    public IndexQueryBuilder notFull(long termId) {

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
