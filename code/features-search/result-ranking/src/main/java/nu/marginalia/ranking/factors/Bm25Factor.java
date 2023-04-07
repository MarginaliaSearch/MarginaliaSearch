package nu.marginalia.ranking.factors;

import nu.marginalia.index.client.model.results.Bm25Parameters;
import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.ranking.ResultKeywordSet;

public class Bm25Factor {
    private static final int AVG_LENGTH = 5000;

    /** This is an estimation of <a href="https://en.wikipedia.org/wiki/Okapi_BM25">BM-25</a>.
     *
     * @see Bm25Parameters
     */
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

    /** Bm25 calculation, except instead of counting positions in the document,
     *  the number of relevance signals for the term is counted instead.
     */
    public double calculateBm25Prio(Bm25Parameters bm25Parameters, ResultKeywordSet keywordSet, ResultRankingContext ctx) {
        final int docCount = ctx.termFreqDocCount();

        double sum = 0.;

        long mask = WordFlags.Site.asBit()
                | WordFlags.SiteAdjacent.asBit()
                | WordFlags.UrlPath.asBit()
                | WordFlags.UrlDomain.asBit()
                | WordFlags.Subjects.asBit();

        for (var keyword : keywordSet.keywords()) {
            double count = Long.bitCount(keyword.encodedWordMetadata() & mask);

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
