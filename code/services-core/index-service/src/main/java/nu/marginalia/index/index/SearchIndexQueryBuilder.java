package nu.marginalia.index.index;

import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.index.priority.ReverseIndexPriorityReader;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.query.IndexQueryBuilder;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import nu.marginalia.index.full.ReverseIndexFullReader;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SearchIndexQueryBuilder implements IndexQueryBuilder  {
    private final IndexQuery query;
    private final ReverseIndexFullReader reverseIndexFullReader;
    private final ReverseIndexPriorityReader reverseIndexPrioReader;

    /* Keep track of already added include terms to avoid redundant checks.
     *
     * Warning: This may cause unexpected behavior if for example attempting to
     * first check one index and then another for the same term. At the moment, that
     * makes no sense, but in the future, that might be a thing one might want to do.
     * */
    private final TIntHashSet alreadyConsideredTerms = new TIntHashSet();

    SearchIndexQueryBuilder(ReverseIndexFullReader reverseIndexFullReader,
                            ReverseIndexPriorityReader reverseIndexPrioReader,
                            IndexQuery query, int... sourceTerms)
    {
        this.query = query;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPrioReader = reverseIndexPrioReader;

        alreadyConsideredTerms.addAll(sourceTerms);
    }

    public IndexQueryBuilder alsoFull(int termId) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(reverseIndexFullReader.also(termId));
        }

        return this;
    }

    public IndexQueryBuilder alsoPrio(int termId) {

        if (alreadyConsideredTerms.add(termId)) {
            query.addInclusionFilter(reverseIndexPrioReader.also(termId));
        }

        return this;
    }

    public IndexQueryBuilder alsoPrioAnyOf(int... termIds) {

        QueryFilterStepIf step;

        if (termIds.length == 0) {
            step = QueryFilterStepIf.noPass();
        }
        else if (termIds.length == 1) {
            return alsoPrio(termIds[0]);
        }
        else {
            var steps = IntStream.of(termIds)
                    .filter(alreadyConsideredTerms::add)
                    .mapToObj(reverseIndexPrioReader::also)
                    .collect(Collectors.toList());

            if (steps.isEmpty())
                return this;

            step = QueryFilterStepIf.anyOf(steps);
        }

        query.addInclusionFilter(step);

        return this;
    }

    public IndexQueryBuilder notFull(int termId) {

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
