package nu.marginalia.index.index;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.index.FullReverseIndexReader;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.query.filter.QueryFilterStepIf;

public class IndexQueryBuilderImpl implements IndexQueryBuilder  {
    private final IndexQuery query;
    private final FullReverseIndexReader reverseIndexFullReader;

    /* Keep track of already added include terms to avoid redundant checks.
     *
     * Warning: This may cause unexpected behavior if for example attempting to
     * first check one index and then another for the same term. At the moment, that
     * makes no sense, but in the future, that might be a thing one might want to do.
     * */
    private final TLongHashSet alreadyConsideredTerms = new TLongHashSet();

    IndexQueryBuilderImpl(FullReverseIndexReader reverseIndexFullReader, IndexQuery query)
    {
        this.query = query;
        this.reverseIndexFullReader = reverseIndexFullReader;
    }

    public IndexQueryBuilder withSourceTerms(long... sourceTerms) {
        alreadyConsideredTerms.addAll(sourceTerms);

        return this;
    }

    public IndexQueryBuilder also(long termId, IndexSearchBudget budget) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(reverseIndexFullReader.also(termId, budget));
        }

        return this;
    }

    public IndexQueryBuilder not(long termId) {

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
