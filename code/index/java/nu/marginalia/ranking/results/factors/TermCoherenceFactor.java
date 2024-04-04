package nu.marginalia.ranking.results.factors;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.model.idx.WordMetadata;

/** Rewards documents where terms appear frequently within the same sentences
 */
public class TermCoherenceFactor {

    public double calculate(CompiledQuery<SearchResultKeywordScore> scores) {
        long mask = CompiledQueryAggregates.longBitmaskAggregate(scores, score -> score.positions() & WordMetadata.POSITIONS_MASK);

        return bitsSetFactor(mask);
    }

    double bitsSetFactor(long mask) {
        final int bitsSetInMask = Long.bitCount(mask);

        return Math.pow(bitsSetInMask/(float) WordMetadata.POSITIONS_COUNT, 0.25);
    }


}