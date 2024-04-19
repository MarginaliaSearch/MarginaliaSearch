package nu.marginalia.ranking.results;

import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingDetails;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingInputs;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingOutputs;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.ranking.results.factors.*;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@Singleton
public class ResultValuator {
    final static double scalingFactor = 500.;

    private final TermCoherenceFactor termCoherenceFactor;

    private static final Logger logger = LoggerFactory.getLogger(ResultValuator.class);

    @Inject
    public ResultValuator(TermCoherenceFactor termCoherenceFactor) {
        this.termCoherenceFactor = termCoherenceFactor;
    }

    public double calculateSearchResultValue(CompiledQueryLong wordMeta,
                                             long documentMetadata,
                                             int features,
                                             int length,
                                             ResultRankingContext ctx,
                                             @Nullable Consumer<ResultRankingDetails> detailsConsumer
                                             )
    {
        if (wordMeta.isEmpty())
            return Double.MAX_VALUE;

        if (length < 0) {
            length = 5000;
        }

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
                           + flagsPenalty;

        double tcfOverlap = rankingParams.tcfOverlapWeight * termCoherenceFactor.calculateOverlap(wordMeta);
        double tcfJaccard = rankingParams.tcfJaccardWeight * termCoherenceFactor.calculateAvgMutualJaccard(wordMeta, ctx);

        double bM25F = rankingParams.bm25FullWeight * wordMeta.root.visit(Bm25FullGraphVisitor.forRegular(rankingParams.fullParams, wordMeta.data, length, ctx));
        double bM25N = 0.25 * rankingParams.bm25FullWeight * wordMeta.root.visit(Bm25FullGraphVisitor.forNgrams(rankingParams.fullParams, wordMeta.data, length, ctx));
        double bM25P = rankingParams.bm25PrioWeight * wordMeta.root.visit(new Bm25PrioGraphVisitor(rankingParams.prioParams, wordMeta.data, ctx));

        double overallPartPositive = Math.max(0, overallPart);
        double overallPartNegative = -Math.min(0, overallPart);

        if (null != detailsConsumer) {
            var details = new ResultRankingDetails(
                    new ResultRankingInputs(
                            rank,
                            asl,
                            quality,
                            size,
                            topology,
                            year,
                            DocumentFlags.decode(documentMetadata).stream().map(Enum::name).toList()
                    ),
                    new ResultRankingOutputs(
                            averageSentenceLengthPenalty,
                            qualityPenalty,
                            rankingBonus,
                            topologyBonus,
                            documentLengthPenalty,
                            temporalBias,
                            flagsPenalty,
                            overallPart,
                            tcfOverlap,
                            tcfJaccard,
                            bM25F,
                            bM25N,
                            bM25P)
            );

            detailsConsumer.accept(details);
        }

        // Renormalize to 0...15, where 0 is the best possible score;
        // this is a historical artifact of the original ranking function
        return normalize(
                      tcfOverlap + tcfJaccard
                      + bM25F + bM25P + bM25N
                      + overallPartPositive,
                overallPartNegative);
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

    public static double normalize(double value, double penalty) {
        if (value < 0)
            value = 0;

        return Math.sqrt((1.0 + scalingFactor + 10 * penalty) / (1.0 + value));
    }
}
