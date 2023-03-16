package nu.marginalia.ranking.factors;

import nu.marginalia.ranking.ResultKeywordSet;

/** Rewards documents where terms appear frequently within the same sentences,
 * and where this overlap is early in the document
 */
public class TermCoherenceFactor {

    public double calculate(ResultKeywordSet keywordSet) {
        int mask = combinedMask(keywordSet);

        return bitsSetFactor(mask) * (0.8 + 0.2 * bitPositionFactor(mask));
    }

    double bitsSetFactor(int mask) {
        final int bitsSetInMask = Integer.bitCount(mask);

        return Math.pow(bitsSetInMask/32.0, 0.25);
    }

    double bitPositionFactor(int mask) {
        int start = Integer.numberOfTrailingZeros(mask);

        return 1 - (start)/32.0;
    }

    int combinedMask(ResultKeywordSet keywordSet) {
        int mask = ~0;

        for (var keyword : keywordSet) {
            long positions = keyword.positions();

            mask &= positions;
        }

        return mask;
    }

}