package nu.marginalia.ranking.factors;

import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.ranking.ResultKeywordSet;

import java.util.List;

/** Rewards results that have a priority term */
public class PriorityTermFactor {
    public double calculate(List<SearchResultKeywordScore> scores) {

        for (var result : scores) {
            if (result.hasPriorityTerms()) {
                return 0.5;
            }
        }

        return 1.0;
    }

    public double calculate(ResultKeywordSet set) {
        for (var result : set) {
            if (result.hasPriorityTerms()) {
                return 0.5;
            }
        }

        return 1.0;
    }
}
