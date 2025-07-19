package nu.marginalia.domainranking.accumulator;

import org.roaringbitmap.RoaringBitmap;

public class RankingResultBitSetAccumulator implements RankingResultAccumulator<RoaringBitmap> {
    private final RoaringBitmap result = new RoaringBitmap();

    @Override
    public void add(int domainId, int rank) {
        result.add(domainId);
    }

    @Override
    public RoaringBitmap get() {
        return result;
    }
}
