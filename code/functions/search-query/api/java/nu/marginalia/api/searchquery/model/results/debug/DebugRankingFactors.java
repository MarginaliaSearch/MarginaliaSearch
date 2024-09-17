package nu.marginalia.api.searchquery.model.results.debug;

import it.unimi.dsi.fastutil.ints.IntIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Utility for capturing debug information about ranking factors */
public class DebugRankingFactors {
    private final List<DebugFactor> documentFactors = new ArrayList<>();
    private final List<DebugTermFactor> termFactors = new ArrayList<>();

    public DebugRankingFactors() {}

    public void addDocumentFactor(String factor, String value) {
        documentFactors.add(new DebugFactor(factor, value));
    }

    public void addTermFactor(long termId, String factor, String value) {
        termFactors.add(new DebugTermFactor(termId, null, factor, value));
    }
    public void addTermFactor(long termId, String factor, IntIterator sequenceIter) {
        if (!sequenceIter.hasNext()) return;

        StringJoiner joiner = new StringJoiner(",");
        while (sequenceIter.hasNext()) {
            joiner.add(String.valueOf(sequenceIter.nextInt()));
        }
        termFactors.add(new DebugTermFactor(termId, null, factor, joiner.toString()));
    }

    public List<DebugFactor> getDocumentFactors() {
        return documentFactors;
    }
    public List<DebugTermFactor> getTermFactors() {
        return termFactors;
    }
}
