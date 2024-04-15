package nu.marginalia.ranking.results.factors;

import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.model.idx.WordMetadata;

/** Rewards documents where terms appear frequently within the same sentences
 */
public class TermCoherenceFactor {

    public double calculate(CompiledQueryLong wordMetadataQuery) {
        long mask = CompiledQueryAggregates.longBitmaskAggregate(wordMetadataQuery,
                score -> score >>> WordMetadata.POSITIONS_SHIFT);

        return bitsSetFactor(mask);
    }

    double bitsSetFactor(long mask) {
        final int bitsSetInMask = Long.bitCount(mask);

        return Math.pow(bitsSetInMask/(float) WordMetadata.POSITIONS_COUNT, 0.25);
    }


}