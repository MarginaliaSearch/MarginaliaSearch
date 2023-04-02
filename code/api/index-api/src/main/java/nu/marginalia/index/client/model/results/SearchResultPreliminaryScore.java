package nu.marginalia.index.client.model.results;

import nu.marginalia.model.idx.DocumentMetadata;
import org.jetbrains.annotations.NotNull;

import static java.lang.Boolean.compare;
import static java.lang.Integer.compare;

public record SearchResultPreliminaryScore(boolean hasSingleTermMatch,
                                           boolean hasPriorityTerm,
                                           int minNumberOfFlagsSet,
                                           int minNumberOfPositions,
                                           int overlappingPositions,
                                           boolean anyAllSynthetic,
                                           int avgSentenceLength,
                                           int topology
                                           )
        implements Comparable<SearchResultPreliminaryScore>
{

    public SearchResultPreliminaryScore(long documentMetadata,
                                        boolean hasSingleTermMatch,
                                        boolean hasPriorityTerm,
                                        int minNumberOfFlagsSet,
                                        int minNumberOfPositions,
                                        int overlappingPositions,
                                        boolean anyAllSynthetic
                                        )
    {
        this(hasSingleTermMatch, hasPriorityTerm, minNumberOfFlagsSet, minNumberOfPositions, overlappingPositions, anyAllSynthetic,
                DocumentMetadata.decodeAvgSentenceLength(documentMetadata),
                DocumentMetadata.decodeTopology(documentMetadata)
                );
    }

    @Override
    public int compareTo(@NotNull SearchResultPreliminaryScore other) {
        int diff;

        diff = -compare(avgSentenceLength >= 2, other.avgSentenceLength >= 2);
        if (diff != 0) return diff;

        diff = compare(hasSingleTermMatch, other.hasSingleTermMatch);
        if (diff != 0) return diff;

        diff = compare(minNumberOfFlagsSet, other.minNumberOfFlagsSet);
        if (diff != 0) return diff;

        diff = compare(hasPriorityTerm, other.hasPriorityTerm);
        if (diff != 0) return diff;

        diff = compare(overlappingPositions, other.overlappingPositions);
        if (diff != 0) return diff;

        diff = compare(minNumberOfPositions, other.minNumberOfPositions);
        if (diff != 0) return diff;

        return -compare(topology, other.topology);
    }

    public boolean isEmpty() {
        return minNumberOfFlagsSet == 0
            && minNumberOfPositions == 0
            && overlappingPositions == 0
            && !anyAllSynthetic;
    }
}
