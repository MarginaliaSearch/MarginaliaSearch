package nu.marginalia.api.searchquery.model.results;

import org.jetbrains.annotations.NotNull;

import static java.lang.Boolean.compare;
import static java.lang.Double.compare;

public record SearchResultPreliminaryScore(
        double searchRankingScore)
        implements Comparable<SearchResultPreliminaryScore>
{

    final static int PREFER_HIGH = 1;
    final static int PREFER_LOW = -1;

    @Override
    public int compareTo(@NotNull SearchResultPreliminaryScore other) {
        int diff;

        return PREFER_LOW * compare(searchRankingScore, other.searchRankingScore);
    }

}
