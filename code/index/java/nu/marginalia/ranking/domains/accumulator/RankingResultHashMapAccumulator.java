package nu.marginalia.ranking.domains.accumulator;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class RankingResultHashMapAccumulator implements RankingResultAccumulator<Int2IntOpenHashMap> {
    private final Int2IntOpenHashMap result;

    public RankingResultHashMapAccumulator(int size) {
         result = new Int2IntOpenHashMap(size);
    }

    @Override
    public void add(int domainId, int rank) {
        result.put(domainId, rank);
    }

    @Override
    public Int2IntOpenHashMap get() {
        return result;
    }
}
