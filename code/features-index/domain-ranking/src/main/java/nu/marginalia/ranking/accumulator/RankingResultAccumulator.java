package nu.marginalia.ranking.accumulator;

public interface RankingResultAccumulator<T> {
    void add(int domainId, int rank);
    T get();
}
