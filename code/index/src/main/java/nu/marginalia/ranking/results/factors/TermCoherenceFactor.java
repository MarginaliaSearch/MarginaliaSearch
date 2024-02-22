package nu.marginalia.ranking.results.factors;

import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.ranking.results.ResultKeywordSet;

/** Rewards documents where terms appear frequently within the same sentences
 */
public class TermCoherenceFactor {

    public double calculate(ResultKeywordSet keywordSet) {
        long mask = combinedMask(keywordSet);

        return bitsSetFactor(mask);
    }

    double bitsSetFactor(long mask) {
        final int bitsSetInMask = Long.bitCount(mask);

        return Math.pow(bitsSetInMask/(float) WordMetadata.POSITIONS_COUNT, 0.25);
    }

    long combinedMask(ResultKeywordSet keywordSet) {
        long mask = WordMetadata.POSITIONS_MASK;

        for (var keyword : keywordSet.keywords()) {
            mask &= keyword.positions();
        }

        return mask;
    }

}