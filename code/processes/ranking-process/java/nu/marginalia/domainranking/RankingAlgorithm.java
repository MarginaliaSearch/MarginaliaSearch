package nu.marginalia.domainranking;

import nu.marginalia.domainranking.accumulator.RankingResultAccumulator;

import java.util.function.Supplier;

public interface RankingAlgorithm {

    /** Calculate domain rankings.
     *
     * @param resultCount update the best result count results
     * @param accumulatorP the accumulator to use to store the results
     */
    <T> T calculate(int resultCount, Supplier<RankingResultAccumulator<T>> accumulatorP);
}
