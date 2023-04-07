package nu.marginalia.index.client.model.results;

import org.jetbrains.annotations.NotNull;

import static java.lang.Boolean.compare;
import static java.lang.Double.compare;

public record SearchResultPreliminaryScore(
        boolean anyAllSynthetic,
        int minNumberOfFlagsSet,
        int minPositionsSet,
        boolean hasPriorityTerm,
        double searchRankingScore)
        implements Comparable<SearchResultPreliminaryScore>
{

    final static int PREFER_HIGH = 1;
    final static int PREFER_LOW = -1;

    @Override
    public int compareTo(@NotNull SearchResultPreliminaryScore other) {
        int diff;

        diff = PREFER_HIGH * compare(hasPriorityTerm, other.hasPriorityTerm);
        if (diff != 0) return diff;

        return PREFER_LOW * compare(searchRankingScore, other.searchRankingScore);
    }

    public boolean isEmpty() {
        if (minNumberOfFlagsSet > 0)
            return false;

        if (anyAllSynthetic)
            return false;

        if (minPositionsSet > 0)
            return false;

        return true;
    }
}
