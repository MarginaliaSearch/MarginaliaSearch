package nu.marginalia.wmsa.edge.index.ranking.accumulator;

public interface RankingResultAccumulator<T> {
    void add(int domainId, int rank);
    T get();
}
