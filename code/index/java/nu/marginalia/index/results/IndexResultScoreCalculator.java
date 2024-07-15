package nu.marginalia.index.results;

import nu.marginalia.api.searchquery.model.compiled.*;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.sequence.SequenceOperations;

import javax.annotation.Nullable;

import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.*;

/** This class is responsible for calculating the score of a search result.
 * It holds the data required to perform the scoring, as there is strong
 * reasons to cache this data, and performs the calculations */
public class IndexResultScoreCalculator {
    private final CombinedIndexReader index;
    private final QueryParams queryParams;

    private final ResultRankingContext rankingContext;
    private final CompiledQuery<String> compiledQuery;

    public IndexResultScoreCalculator(StatefulIndex statefulIndex,
                                      ResultRankingContext rankingContext,
                                      SearchParameters params)
    {
        this.index = statefulIndex.get();
        this.rankingContext = rankingContext;

        this.queryParams = params.queryParams;
        this.compiledQuery = params.compiledQuery;
    }

    private final long flagsFilterMask = WordFlags.Title.asBit() | WordFlags.Subjects.asBit() | WordFlags.UrlDomain.asBit() | WordFlags.UrlPath.asBit() | WordFlags.ExternalLink.asBit();

    @Nullable
    public SearchResultItem calculateScore(long combinedId,
                                           QuerySearchTerms searchTerms,
                                           long[] wordFlags,
                                           GammaCodedSequence[] positions)
    {

        CompiledQuery<GammaCodedSequence> positionsQuery = compiledQuery.root.newQuery(positions);

        int[] counts = new int[compiledQuery.size()];

        for (int i = 0; i < counts.length; i++) {
            if (positions[i] != null) {
                counts[i] = positions[i].valueCount();
            }
        }
        CompiledQueryInt positionsCountQuery = compiledQuery.root.newQuery(counts);
        CompiledQueryLong wordFlagsQuery = compiledQuery.root.newQuery(wordFlags);

        // If the document is not relevant to the query, abort early to reduce allocations and
        // avoid unnecessary calculations
        if (testRelevance(wordFlagsQuery, positionsCountQuery)) {
            return null;
        }

        long docId = UrlIdCodec.removeRank(combinedId);
        long docMetadata = index.getDocumentMetadata(docId);
        int htmlFeatures = index.getHtmlFeatures(docId);
        int docSize = index.getDocumentSize(docId);

        int bestCoherence = searchTerms.coherences.testOptional(positions);

        double score = calculateSearchResultValue(
                wordFlagsQuery,
                positionsCountQuery,
                positionsQuery,
                docMetadata,
                htmlFeatures,
                docSize,
                bestCoherence,
                rankingContext);

        SearchResultItem searchResult = new SearchResultItem(docId,
                docMetadata,
                htmlFeatures);

        if (hasPrioTerm(searchTerms, positions)) {
            score = 0.75 * score;
        }

        searchResult.setScore(score);

        return searchResult;
    }

    private boolean testRelevance(CompiledQueryLong wordFlagsQuery, CompiledQueryInt countsQuery) {
        boolean allSynthetic = booleanAggregate(wordFlagsQuery, WordFlags.Synthetic::isPresent);
        int flagsCount = intMaxMinAggregate(wordFlagsQuery, flags ->  Long.bitCount(flags & flagsFilterMask));
        int positionsCount = intMaxMinAggregate(countsQuery, p -> p);

        if (!meetsQueryStrategyRequirements(wordFlagsQuery, queryParams.queryStrategy())) {
            return true;
        }
        if (flagsCount == 0 && !allSynthetic && positionsCount == 0) {
            return true;
        }

        return false;
    }

    private boolean hasPrioTerm(QuerySearchTerms searchTerms, GammaCodedSequence[] positions) {
        var allTerms = searchTerms.termIdsAll;
        var prioTerms = searchTerms.termIdsPrio;

        for (int i = 0; i < allTerms.size(); i++) {
            if (positions[i] != null && prioTerms.contains(allTerms.at(i))) {
                return true;
            }
        }

        return false;
    }

    private boolean meetsQueryStrategyRequirements(CompiledQueryLong queryGraphScores,
                                                   QueryStrategy queryStrategy)
    {
        if (queryStrategy == QueryStrategy.AUTO ||
                queryStrategy == QueryStrategy.SENTENCE ||
                queryStrategy == QueryStrategy.TOPIC) {
            return true;
        }

        return booleanAggregate(queryGraphScores,
                docs -> meetsQueryStrategyRequirements(docs, queryParams.queryStrategy()));
    }

    private boolean meetsQueryStrategyRequirements(long wordMeta, QueryStrategy queryStrategy) {
        if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SITE) {
            return WordFlags.Site.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SUBJECT) {
            return WordFlags.Subjects.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_TITLE) {
            return WordFlags.Title.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_URL) {
            return WordFlags.UrlPath.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_DOMAIN) {
            return WordFlags.UrlDomain.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_LINK) {
            return WordFlags.ExternalLink.isPresent(wordMeta);
        }
        return true;
    }

    public double calculateSearchResultValue(CompiledQueryLong wordFlagsQuery,
                                             CompiledQueryInt positionsCountQuery,
                                             CompiledQuery<GammaCodedSequence> positionsQuery, long documentMetadata,
                                             int features,
                                             int length,
                                             int bestCoherence,
                                             ResultRankingContext ctx)
    {
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
                + flagsPenalty
                + bestCoherence;

        double tcfAvgDist = rankingParams.tcfAvgDist * (1.0 / calculateAvgMinDistance(positionsQuery, ctx));
        double tcfFirstPosition = 0.;

        double bM25 = rankingParams.bm25Weight * wordFlagsQuery.root.visit(new Bm25GraphVisitor(rankingParams.bm25Params, positionsCountQuery.data, length, ctx));

        // Renormalize to 0...15, where 0 is the best possible score;
        // this is a historical artifact of the original ranking function
        double ret = normalize(
                tcfAvgDist + tcfFirstPosition
                        + bM25
                        + Math.max(0, overallPart),
                -Math.min(0, overallPart));

        if (Double.isNaN(ret)) { // This should never happen but if it does, we want to know about it
            if (getClass().desiredAssertionStatus()) {
                throw new IllegalStateException("NaN in result value calculation");
            }

            return Double.MAX_VALUE;
        }
        else {
            return ret;
        }
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

    /** Normalize a value to the range 0...15, where 0 is the best possible score
     *
     * @param value The value to normalize, must be positive or zero
     * @param penalty Any negative component of the value
     * */
    public static double normalize(double value, double penalty) {
        if (value < 0)
            value = 0;

        return Math.sqrt((1.0 + 500. + 10 * penalty) / (1.0 + value));
    }


    public static double calculateAvgMinDistance(CompiledQuery<GammaCodedSequence> positions, ResultRankingContext ctx) {
        double sum = 0;
        int cnt = 0;

        for (int i = 0; i < positions.size(); i++) {

            // Skip terms that are not in the regular mask
            if (!ctx.regularMask.get(i))
                continue;

            var posi = positions.at(i);

            // Skip terms that are not in the document
            if (posi == null)
                continue;

            for (int j = i + 1; j < positions.size(); j++) {

                // Skip terms that are not in the regular mask
                if (!ctx.regularMask.get(j))
                    continue;

                var posj = positions.at(j);

                // Skip terms that are not in the document
                if (posj == null)
                    continue;

                int distance = SequenceOperations.minDistance(posi.iterator(), posj.iterator());
                sum += distance;
                cnt++;
            }
        }

        if (cnt > 0) {
            return sum / cnt;
        } else {
            return 1000.;
        }
    }

}
