package nu.marginalia.ranking.domains.accumulator;

import gnu.trove.list.array.TIntArrayList;

public class RankingResultListAccumulator implements RankingResultAccumulator<TIntArrayList> {
    private final TIntArrayList result;

    public RankingResultListAccumulator(int size) {
         result = new TIntArrayList(size);
    }
    public RankingResultListAccumulator() {
         result = new TIntArrayList(10_000);
    }

    @Override
    public void add(int domainId, int rank) {
        result.add(domainId);
    }

    @Override
    public TIntArrayList get() {
        return result;
    }
}
