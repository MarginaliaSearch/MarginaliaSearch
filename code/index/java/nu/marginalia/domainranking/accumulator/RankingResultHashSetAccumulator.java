package nu.marginalia.domainranking.accumulator;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class RankingResultHashSetAccumulator implements RankingResultAccumulator<IntOpenHashSet> {
    private final IntOpenHashSet result = new IntOpenHashSet();

    @Override
    public void add(int domainId, int rank) {
        result.add(domainId);
    }

    @Override
    public IntOpenHashSet get() {
        return result;
    }
}
