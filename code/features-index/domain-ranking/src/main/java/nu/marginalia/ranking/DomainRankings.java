package nu.marginalia.ranking;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class DomainRankings {
    private final Int2ShortOpenHashMap rankings;

    private final int MAX_MEANINGFUL_RANK = 50_000;
    private final int MAX_RANK_VALUE = 255;
    private final int MIN_RANK_VALUE = 1;
    private final double RANK_SCALING_FACTOR = (double) MAX_RANK_VALUE / MAX_MEANINGFUL_RANK;

    public DomainRankings() {
        rankings = new Int2ShortOpenHashMap();
    }
    public DomainRankings(Int2IntOpenHashMap values) {
         rankings = new Int2ShortOpenHashMap(values.size());
         values.forEach(this::putRanking);
    }

    private void putRanking(int domainId, int value) {
        rankings.put(domainId, scaleRank(value));
    }

    private short scaleRank(int value) {
        double rankScaled = RANK_SCALING_FACTOR * value;
        return (short) min(MAX_RANK_VALUE, max(MIN_RANK_VALUE, rankScaled));
    }

    public int getRanking(int domainId) {
        return rankings.getOrDefault(domainId, (short) MAX_RANK_VALUE);
    }

    public float getSortRanking(int domainId) {
        return rankings.getOrDefault(domainId, (short) MAX_RANK_VALUE) / (float) MAX_RANK_VALUE;
    }

    public int size() {
        return rankings.size();
    }
}
