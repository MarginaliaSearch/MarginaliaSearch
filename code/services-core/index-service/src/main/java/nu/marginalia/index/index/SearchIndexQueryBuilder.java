package nu.marginalia.index.index;

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

    SearchIndexQueryBuilder(ReverseIndexFullReader reverseIndexFullReader,
                            ReverseIndexPriorityReader reverseIndexPrioReader,
                            IndexQuery query)
    {
        this.query = query;
        this.reverseIndexFullReader = reverseIndexFullReader;
        this.reverseIndexPrioReader = reverseIndexPrioReader;
    }

    public IndexQueryBuilder alsoFull(int termId) {

        query.addInclusionFilter(reverseIndexFullReader.also(termId));

        return this;
    }

    public IndexQueryBuilder alsoPrioAnyOf(int... termIds) {

        QueryFilterStepIf step;

        if (termIds.length == 0) {
            step = QueryFilterStepIf.noPass();
        }
        else if (termIds.length == 1) {
            step = reverseIndexPrioReader.also(termIds[0]);
        }
        else {
            var steps = IntStream.of(termIds)
                    .mapToObj(reverseIndexPrioReader::also)
                    .collect(Collectors.toList());
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
