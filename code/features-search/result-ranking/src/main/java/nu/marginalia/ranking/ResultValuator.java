package nu.marginalia.ranking;

import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.ranking.factors.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

@Singleton
public class ResultValuator {
    final static double scalingFactor = 250.;

    private final Bm25Factor bm25Factor;
    private final TermCoherenceFactor termCoherenceFactor;

    private final PriorityTermBonus priorityTermBonus;

    private final ThreadLocal<ValuatorListPool<SearchResultKeywordScore>> listPool =
            ThreadLocal.withInitial(ValuatorListPool::new);

    @Inject
    public ResultValuator(Bm25Factor bm25Factor,
                          TermCoherenceFactor termCoherenceFactor,
                          PriorityTermBonus priorityTermBonus) {

        this.bm25Factor = bm25Factor;
        this.termCoherenceFactor = termCoherenceFactor;
        this.priorityTermBonus = priorityTermBonus;

    }

    public double calculateSearchResultValue(List<SearchResultKeywordScore> scores,
                                             int length,
                                             ResultRankingContext ctx)
    {
        var threadListPool = listPool.get();
        int sets = numberOfSets(scores);

        double bestScore = 10;

        long documentMetadata = documentMetadata(scores);

        var rankingParams = ctx.params;

        int rank = DocumentMetadata.decodeRank(documentMetadata);
        int asl = DocumentMetadata.decodeAvgSentenceLength(documentMetadata);
        int quality = DocumentMetadata.decodeQuality(documentMetadata);
        int urlTypePenalty = getUrlTypePenalty(documentMetadata);
        int topology = DocumentMetadata.decodeTopology(documentMetadata);
        int year = DocumentMetadata.decodeYear(documentMetadata);

        double averageSentenceLengthPenalty = (asl >= rankingParams.shortSentenceThreshold ? 0 : -rankingParams.shortSentencePenalty);

        final double qualityPenalty = -quality * rankingParams.qualityPenalty;
        final double rankingBonus = (255. - rank) * rankingParams.domainRankBonus;
        final double topologyBonus = Math.log(1 + topology);
        final double documentLengthPenalty = length > rankingParams.shortDocumentThreshold ? 0 : -rankingParams.shortDocumentPenalty;
        final double temporalBias;

        if (rankingParams.temporalBias == ResultRankingParameters.TemporalBias.RECENT) {
            temporalBias = - Math.abs(year - PubDate.MAX_YEAR) * rankingParams.temporalBiasWeight;
        } else if (rankingParams.temporalBias == ResultRankingParameters.TemporalBias.OLD) {
            temporalBias = - Math.abs(year - PubDate.MIN_YEAR) * rankingParams.temporalBiasWeight;
        } else {
            temporalBias = 0;
        }

        double overallPart = averageSentenceLengthPenalty
                           + documentLengthPenalty
                           + qualityPenalty
                           + rankingBonus
                           + topologyBonus
                           + temporalBias
                           + urlTypePenalty
                           + priorityTermBonus.calculate(scores);

        for (int set = 0; set <= sets; set++) {
            ResultKeywordSet keywordSet = createKeywordSet(threadListPool, scores, set);

            if (keywordSet.isEmpty() || keywordSet.hasNgram())
                continue;

            final double tcf = rankingParams.tcfWeight * termCoherenceFactor.calculate(keywordSet);
            final double bm25 = rankingParams.bm25FullWeight * bm25Factor.calculateBm25(rankingParams.fullParams, keywordSet, length, ctx);
            final double bm25p = rankingParams.bm25PrioWeight * bm25Factor.calculateBm25Prio(rankingParams.prioParams, keywordSet, ctx);

            double score = normalize(bm25 + bm25p + tcf + overallPart, keywordSet.length());

            bestScore = min(bestScore, score);

        }

        return bestScore;
    }

    private int getUrlTypePenalty(long documentMetadata) {

        // Long urls-that-look-like-this tend to be poor search results
        if (DocumentMetadata.hasFlags(documentMetadata,
                HtmlFeature.LONG_URL.getFeatureBit()
                      | HtmlFeature.KEBAB_CASE_URL.getFeatureBit())) {
            return 2;
        }

        return 0;
    }

    private long documentMetadata(List<SearchResultKeywordScore> rawScores) {
        for (var score : rawScores) {
            return score.encodedDocMetadata();
        }
        return 0;
    }

    private ResultKeywordSet createKeywordSet(ValuatorListPool<SearchResultKeywordScore> listPool,
                                              List<SearchResultKeywordScore> rawScores,
                                              int thisSet)
    {
        List<SearchResultKeywordScore> scoresList = listPool.get(thisSet);
        scoresList.clear();

        for (var score : rawScores) {
            if (score.subquery != thisSet)
                continue;

            // Don't consider synthetic keywords for ranking, these are keywords that don't
            // have counts. E.g. "tld:edu"
            if (score.isKeywordSpecial())
                continue;

            scoresList.add(score);
        }

        return new ResultKeywordSet(scoresList);

    }

    private int numberOfSets(List<SearchResultKeywordScore> scores) {
        int maxSet = 0;

        for (var score : scores) {
            maxSet = Math.max(maxSet, score.subquery);
        }

        return 1 + maxSet;
    }

    public static double normalize(double value, int setSize) {
        if (value < 0)
            value = 0;

        return Math.sqrt((1.0 + scalingFactor) / (1.0 + value / Math.max(1., setSize)));
    }
}

/** Pool of List instances used to reduce memory churn during result ranking in the index
 * where potentially tens of thousands of candidate results are ranked.
 *
 * @param <T>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ValuatorListPool<T> {
    private final ArrayList[] items = new ArrayList[256];

    public ValuatorListPool() {
        for (int i  = 0; i < items.length; i++) {
            items[i] = new ArrayList();
        }
    }

    public List<T> get(int i) {
        var ret = (ArrayList<T>) items[i];
        ret.clear();
        return ret;
    }

}
