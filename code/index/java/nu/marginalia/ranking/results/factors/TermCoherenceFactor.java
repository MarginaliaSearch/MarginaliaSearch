package nu.marginalia.ranking.results.factors;

import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.model.idx.WordMetadata;

/** Rewards documents where terms appear frequently within the same sentences
 */
public class TermCoherenceFactor {

    /** Calculate a factor that rewards the best total position overlap
     * between the terms in the query.  This is high when all the terms
     * found in the same sentences.
     */
    public double calculateOverlap(CompiledQueryLong wordMetadataQuery) {
        if (wordMetadataQuery.size() <= 2)
            return 0;

        long mask = CompiledQueryAggregates.longBitmaskAggregate(wordMetadataQuery,
                score -> score >>> WordMetadata.POSITIONS_SHIFT);

        return bitsSetFactor(mask);
    }

    /** Calculate a factor that rewards the best average mutual Jaccard index
     * between the terms in the query.  This is high when the several terms are frequently
     * found in the same sentences.
     */
    public double calculateAvgMutualJaccard(CompiledQueryLong wordMetadataQuery, ResultRankingContext ctx) {
        double sum = 0;
        int cnt = 0;

        for (int i = 0; i < wordMetadataQuery.size(); i++) {

            // Skip terms that are not in the regular mask
            if (!ctx.regularMask.get(i))
                continue;

            long imask = WordMetadata.decodePositions(wordMetadataQuery.at(i));

            // Skip terms that are not in the document
            if (imask == 0L)
                continue;

            for (int j = i + 1; j < wordMetadataQuery.size(); j++) {

                // Skip terms that are not in the regular mask
                if (!ctx.regularMask.get(j))
                    continue;

                long jmask = WordMetadata.decodePositions(wordMetadataQuery.at(j));

                // Skip terms that are not in the document
                if (jmask == 0L)
                    continue;

                long quot = Long.bitCount(imask & jmask);
                long rem = Long.bitCount(imask | jmask);

                // rem is always > 0 because imask and jmask are not both 0

                sum += quot/(double) rem;
                cnt++;
            }
        }

        if (cnt > 0) {
            return sum / cnt;
        } else {
            return 0;
        }
    }

    double bitsSetFactor(long mask) {
        final int bitsSetInMask = Long.bitCount(mask);

        return Math.pow(bitsSetInMask/(double) WordMetadata.POSITIONS_COUNT, 0.25);
    }


}