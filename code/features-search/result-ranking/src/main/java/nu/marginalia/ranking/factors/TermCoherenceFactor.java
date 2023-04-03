package nu.marginalia.ranking.factors;

import nu.marginalia.ranking.ResultKeywordSet;

/** Rewards documents where terms appear frequently within the same sentences,
 * and where this overlap is early in the document
 */
public class TermCoherenceFactor {

    public double calculate(ResultKeywordSet keywordSet) {
        long mask = combinedMask(keywordSet);

        return bitsSetFactor(mask) * (0.8 + 0.2 * bitPositionFactor(mask));
    }

    double bitsSetFactor(long mask) {
        final int bitsSetInMask = Long.bitCount(mask);

        return Math.pow(bitsSetInMask/48., 0.25);
    }

    double bitPositionFactor(long mask) {
        int start = Math.min(48, Long.numberOfTrailingZeros(mask));

        return 1 - start/48.;
    }

    long combinedMask(ResultKeywordSet keywordSet) {
        long mask = 0xFFFF_FFFF_FFFFL;

        for (var keyword : keywordSet) {
            long positions = keyword.positions();

            mask &= positions;
        }

        return mask;
    }

}