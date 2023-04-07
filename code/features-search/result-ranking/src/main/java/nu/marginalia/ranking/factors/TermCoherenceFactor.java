package nu.marginalia.ranking.factors;

import nu.marginalia.ranking.ResultKeywordSet;

/** Rewards documents where terms appear frequently within the same sentences
 */
public class TermCoherenceFactor {

    public double calculate(ResultKeywordSet keywordSet) {
        long mask = combinedMask(keywordSet);

        return bitsSetFactor(mask);
    }

    double bitsSetFactor(long mask) {
        final int bitsSetInMask = Long.bitCount(mask);

        return Math.pow(bitsSetInMask/56., 0.25);
    }

    long combinedMask(ResultKeywordSet keywordSet) {
        long mask = 0xFF_FFFF_FFFF_FFFFL;

        for (var keyword : keywordSet.keywords()) {
            long positions = keyword.positions();

            mask &= positions;
        }

        return mask;
    }

}