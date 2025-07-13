package nu.marginalia.domainranking.accumulator;

public interface RankingResultAccumulator<T> {
    void add(int domainId, int rank);
    T get();
}
