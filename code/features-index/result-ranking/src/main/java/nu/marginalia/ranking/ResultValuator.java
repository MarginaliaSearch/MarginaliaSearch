package nu.marginalia.ranking;

import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.ranking.factors.*;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;

@Singleton
public class ResultValuator {
    final static double scalingFactor = 500.;

    private final Bm25Factor bm25Factor;
    private final TermCoherenceFactor termCoherenceFactor;

    private final PriorityTermBonus priorityTermBonus;

    private final ThreadLocal<ValuatorListPool<SearchResultKeywordScore>> listPool =
            ThreadLocal.withInitial(ValuatorListPool::new);

    private static final Logger logger = LoggerFactory.getLogger(ResultValuator.class);

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


        long documentMetadata = documentMetadata(scores);
        int features = htmlFeatures(scores);
        var rankingParams = ctx.params;

        int rank = DocumentMetadata.decodeRank(documentMetadata);
        int asl = DocumentMetadata.decodeAvgSentenceLength(documentMetadata);
        int quality = DocumentMetadata.decodeQuality(documentMetadata);
        int size = DocumentMetadata.decodeSize(documentMetadata);
        int flagsPenalty = flagsPenalty(features, documentMetadata & 0xFF, size);
        int topology = DocumentMetadata.decodeTopology(documentMetadata);
        int year = DocumentMetadata.decodeYear(documentMetadata);

        double averageSentenceLengthPenalty = (asl >= rankingParams.shortSentenceThreshold ? 0 : -rankingParams.shortSentencePenalty);

        final double qualityPenalty = calculateQualityPenalty(size, quality, rankingParams);
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
                           + flagsPenalty
                           + priorityTermBonus.calculate(scores);

        double bestTcf = 0;
        double bestBM25F = 0;
        double bestBM25P = 0;
        double bestBM25PN = 0;

        for (int set = 0; set < sets; set++) {
            ResultKeywordSet keywordSet = createKeywordSet(threadListPool, scores, set);

            if (keywordSet.isEmpty())
                continue;

            bestTcf = Math.max(bestTcf, rankingParams.tcfWeight * termCoherenceFactor.calculate(keywordSet));
            bestBM25P = Math.max(bestBM25P, rankingParams.bm25PrioWeight * bm25Factor.calculateBm25Prio(rankingParams.prioParams, keywordSet, ctx));
            bestBM25F = Math.max(bestBM25F, rankingParams.bm25FullWeight * bm25Factor.calculateBm25(rankingParams.fullParams, keywordSet, length, ctx));
            if (keywordSet.hasNgram()) {
                bestBM25PN = Math.max(bestBM25PN, rankingParams.bm25PrioWeight * bm25Factor.calculateBm25Prio(rankingParams.prioParams, keywordSet, ctx));
            }
        }


        double overallPartPositive = Math.max(0, overallPart);
        double overallPartNegative = -Math.min(0, overallPart);

        // Renormalize to 0...15, where 0 is the best possible score;
        // this is a historical artifact of the original ranking function
        return normalize(1.5 * bestTcf + bestBM25F + bestBM25P + 0.25 * bestBM25PN + overallPartPositive, overallPartNegative);
    }

    private double calculateQualityPenalty(int size, int quality, ResultRankingParameters rankingParams) {
        if (size < 400) {
            if (quality < 5)
                return 0;
            return -quality * rankingParams.qualityPenalty;
        }
        else {
            return -quality * rankingParams.qualityPenalty * 20;
        }
    }

    private int flagsPenalty(int featureFlags, long docFlags, int size) {

        // Short-circuit for index-service, which does not have the feature flags
        if (featureFlags == 0)
            return 0;

        double penalty = 0;

        boolean isForum = DocumentFlags.GeneratorForum.isPresent(docFlags);
        boolean isWiki = DocumentFlags.GeneratorWiki.isPresent(docFlags);
        boolean isDocs = DocumentFlags.GeneratorDocs.isPresent(docFlags);

        // Penalize large sites harder for any bullshit as it's a strong signal of a low quality site
        double largeSiteFactor = 1.;

        if (!isForum && !isWiki && !isDocs && size > 400) {
            // Long urls-that-look-like-this tend to be poor search results
            if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.KEBAB_CASE_URL.getFeatureBit()))
                penalty += 30.0;
            else if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.LONG_URL.getFeatureBit()))
                penalty += 30.;
            else penalty += 5.;

            largeSiteFactor = 2;
        }

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.TRACKING_ADTECH.getFeatureBit()))
            penalty += 7.5 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.AFFILIATE_LINK.getFeatureBit()))
            penalty += 5.0 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.COOKIES.getFeatureBit()))
            penalty += 2.5 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.TRACKING.getFeatureBit()))
            penalty += 2.5 * largeSiteFactor;

        if (isForum || isWiki) {
            penalty = Math.min(0, penalty - 2);
        }

        return (int) -penalty;
    }

    private long documentMetadata(List<SearchResultKeywordScore> rawScores) {
        for (var score : rawScores) {
            return score.encodedDocMetadata();
        }
        return 0;
    }

    private int htmlFeatures(List<SearchResultKeywordScore> rawScores) {
        for (var score : rawScores) {
            return score.htmlFeatures();
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

    public static double normalize(double value, double penalty) {
        if (value < 0)
            value = 0;

        return Math.sqrt((1.0 + scalingFactor + 5 * penalty) / (1.0 + value));
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
