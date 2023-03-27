package nu.marginalia.index.client.model.results;

import org.jetbrains.annotations.NotNull;

import static java.lang.Boolean.compare;
import static java.lang.Integer.compare;

public record SearchResultPreliminaryScore(boolean hasSingleTermMatch,
                                           boolean hasPriorityTerm,
                                           int minNumberOfFlagsSet,
                                           int minNumberOfPositions,
                                           int overlappingPositions,
                                           boolean anyAllSynthetic)
        implements Comparable<SearchResultPreliminaryScore>
{
    @Override
    public int compareTo(@NotNull SearchResultPreliminaryScore other) {
        int diff;

        diff = compare(hasSingleTermMatch, other.hasSingleTermMatch);
        if (diff != 0) return diff;

        diff = compare(minNumberOfFlagsSet, other.minNumberOfFlagsSet);
        if (diff != 0) return diff;

        diff = compare(hasPriorityTerm, other.hasPriorityTerm);
        if (diff != 0) return diff;

        diff = compare(overlappingPositions, other.overlappingPositions);
        if (diff != 0) return diff;

        return compare(minNumberOfPositions, other.minNumberOfPositions);
    }

    public boolean isGreat() {
        return hasSingleTermMatch || (minNumberOfFlagsSet >= 1 && overlappingPositions >= 1);
    }
    public boolean isEmpty() {
        return minNumberOfFlagsSet == 0
            && minNumberOfPositions == 0
            && overlappingPositions == 0
            && !anyAllSynthetic;
    }
}
