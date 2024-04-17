package nu.marginalia.ranking.results.factors;

import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.model.idx.WordMetadata;

/** Rewards documents where terms appear frequently within the same sentences
 */
public class TermCoherenceFactor {

    public double calculateOverlap(CompiledQueryLong wordMetadataQuery) {
        long mask = CompiledQueryAggregates.longBitmaskAggregate(wordMetadataQuery,
                score -> score >>> WordMetadata.POSITIONS_SHIFT);

        return bitsSetFactor(mask);
    }

    public double calculateAvgMutualJaccard(CompiledQueryLong wordMetadataQuery, ResultRankingContext ctx) {
        double sum = 0;
        int cnt = 0;

        for (int i = 0; i < wordMetadataQuery.size(); i++) {
            if (!ctx.regularMask.get(i)) continue;

            long imask = WordMetadata.decodePositions(wordMetadataQuery.at(i));

            for (int j = i + 1; j < wordMetadataQuery.size(); j++) {
                if (!ctx.regularMask.get(j)) continue;

                long jmask = WordMetadata.decodePositions(wordMetadataQuery.at(j));

                long quot = Long.bitCount(imask & jmask);
                long rem = Long.bitCount(imask | jmask);

                if (rem != 0) {
                    sum += quot/(double) rem;
                    cnt++;
                }
            }
        }

        return sum / cnt;
    }

    double bitsSetFactor(long mask) {
        final int bitsSetInMask = Long.bitCount(mask);

        return Math.pow(bitsSetInMask/(double) WordMetadata.POSITIONS_COUNT, 0.25);
    }


}