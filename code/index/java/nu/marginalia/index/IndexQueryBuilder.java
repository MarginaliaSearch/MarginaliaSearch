package nu.marginalia.index;

import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.index.reverse.FullReverseIndexReader;
import nu.marginalia.index.reverse.IndexLanguageContext;
import nu.marginalia.index.reverse.query.IndexQuery;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.index.reverse.query.ReverseIndexRejectDocumentRangeFilter;
import nu.marginalia.index.reverse.query.ReverseIndexRetainDocumentRangeFilter;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListValueRanges;

public class IndexQueryBuilder {
    private final IndexLanguageContext context;
    private final IndexQuery query;
    private final FullReverseIndexReader reverseIndexFullReader;

    /* Keep track of already added include terms to avoid redundant checks.
     *
     * Warning: This may cause unexpected behavior if for example attempting to
     * first check one index and then another for the same term. At the moment, that
     * makes no sense, but in the future, that might be a thing one might want to do.
     * */
    private final TLongHashSet alreadyConsideredTerms = new TLongHashSet();

    IndexQueryBuilder(FullReverseIndexReader reverseIndexFullReader, IndexLanguageContext context, IndexQuery query)
    {
        this.context = context;
        this.query = query;
        this.reverseIndexFullReader = reverseIndexFullReader;
    }

    public IndexQueryBuilder withSourceTerms(long... sourceTerms) {
        alreadyConsideredTerms.addAll(sourceTerms);

        return this;
    }

    public IndexQueryBuilder also(String term, long termId, IndexSearchBudget budget) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(
                    reverseIndexFullReader.also(context, term, termId, budget)
            );
        }

        return this;
    }

    public IndexQueryBuilder not(String term, long termId, IndexSearchBudget budget) {

        query.addInclusionFilter(
                reverseIndexFullReader.not(context, term, termId, budget)
        );

        return this;
    }

    public IndexQueryBuilder rejectingDomains(SkipListValueRanges ranges) {
        query.addInclusionFilter(
                new ReverseIndexRejectDocumentRangeFilter(
                    new SkipListValueRanges(ranges) // make a copy as these are mutable
            )
        );

        return this;
    }


    public IndexQueryBuilder requiringDomains(SkipListValueRanges ranges) {
        if (query.isFiltered(ranges)) {
            return this; // filter is already applied
        }

        query.addInclusionFilter(
                new ReverseIndexRetainDocumentRangeFilter(
                        new SkipListValueRanges(ranges)  // make a copy as these are mutable
                )
        );

        return this;
    }

    public IndexQueryBuilder addInclusionFilter(QueryFilterStepIf filterStep) {

        query.addInclusionFilter(filterStep);

        return this;
    }

    public boolean isNoOp() {
        return query.isNoOp();
    }

    public IndexQuery build() {
        return query;
    }


}
