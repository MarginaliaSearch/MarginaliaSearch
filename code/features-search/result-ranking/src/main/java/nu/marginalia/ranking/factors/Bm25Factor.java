package nu.marginalia.ranking.factors;

import nu.marginalia.index.client.model.results.Bm25Parameters;
import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.ranking.ResultKeywordSet;

/** This is a fairly coarse estimation of <a href="https://en.wikipedia.org/wiki/Okapi_BM25">BM-25</a>,
 * since document count can't be accurately accessed at this point
 */
public class Bm25Factor {
    private static final int AVG_LENGTH = 5000;

    public double calculateBm25(Bm25Parameters bm25Parameters, ResultKeywordSet keywordSet, int length, ResultRankingContext ctx) {
        final int docCount = ctx.termFreqDocCount();

        if (length <= 0)
            length = AVG_LENGTH;

        double sum = 0.;

        for (var keyword : keywordSet.keywords()) {
            double count = keyword.positionCount();

            int freq = ctx.frequency(keyword.keyword);

            sum += invFreq(docCount, freq) * f(bm25Parameters.k(), bm25Parameters.b(), count, length);
        }

        return sum;
    }

    public double calculateBm25Prio(Bm25Parameters bm25Parameters, ResultKeywordSet keywordSet, ResultRankingContext ctx) {
        final int docCount = ctx.termFreqDocCount();

        double sum = 0.;

        for (var keyword : keywordSet.keywords()) {
            double count = keyword.positionCount();

            int freq = ctx.priorityFrequency(keyword.keyword);

            sum += invFreq(docCount, freq) * f(bm25Parameters.k(), 0, count, 0);
        }

        return sum;
    }

    /**
     *
     * @param docCount Number of documents
     * @param freq Number of matching documents
     */
    private double invFreq(int docCount, int freq) {
        return Math.log(1.0 + (docCount - freq + 0.5) / (freq + 0.5));
    }

    /**
     *
     * @param k  determines the size of the impact of a single term
     * @param b  determines the magnitude of the length normalization
     * @param count   number of occurrences in the document
     * @param length  document length
     */
    private double f(double k, double b, double count, int length) {
        final double lengthRatio = (double) length / AVG_LENGTH;

        return (count * (k + 1)) / (count + k * (1 - b + b * lengthRatio));
    }
}
