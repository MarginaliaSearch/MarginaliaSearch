package nu.marginalia.ranking.factors;

import nu.marginalia.index.client.model.results.SearchResultRankingContext;
import nu.marginalia.ranking.ResultKeywordSet;

/** This is a fairly coarse estimation of <a href="https://en.wikipedia.org/wiki/Okapi_BM25">BM-25</a>,
 * since document count can't be accurately accessed at this point
 */
public class Bm25Factor {
    private static final int AVG_LENGTH = 5000;

    public double calculate(ResultKeywordSet keywordSet, int length, SearchResultRankingContext ctx) {
        final double scalingFactor = 750.;

        final int docCount = ctx.termFreqDocCount();

        final double wf1 = 0.7;
        double k = 2;

        double sum = 0.;

        for (var keyword : keywordSet) {
            double count = keyword.positionCount();

            double wt = ctx.frequency(keyword.keyword);

            final double invFreq = Math.log(1.0 + (docCount - wt + 0.5)/(wt + 0.5));

            sum += invFreq * (count * (k + 1)) / (count + k * (1 - wf1 + wf1 * AVG_LENGTH/length));
        }

        double ret = Math.sqrt((1.0 + scalingFactor) / (1.0 + sum));

        assert (Double.isFinite(ret));

        return ret;
    }

}
