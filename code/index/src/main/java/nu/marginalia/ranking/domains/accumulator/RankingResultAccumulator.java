package nu.marginalia.ranking.domains.accumulator;

public interface RankingResultAccumulator<T> {
    void add(int domainId, int rank);
    T get();
}
