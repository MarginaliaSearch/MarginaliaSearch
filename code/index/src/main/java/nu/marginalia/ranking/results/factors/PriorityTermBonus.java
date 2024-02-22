package nu.marginalia.ranking.results.factors;


import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;

import java.util.List;

/** Rewards results that have a priority term */
public class PriorityTermBonus {
    public double calculate(List<SearchResultKeywordScore> scores) {

        for (var result : scores) {
            if (result.hasPriorityTerms()) {
                return 2.0;
            }
        }

        return 0;
    }

}
