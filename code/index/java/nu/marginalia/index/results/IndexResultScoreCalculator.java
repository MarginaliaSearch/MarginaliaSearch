package nu.marginalia.index.results;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.debug.DebugRankingFactors;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.results.model.PhraseConstraintGroupList;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.SequenceOperations;

import javax.annotation.Nullable;
import java.lang.foreign.Arena;
import java.util.BitSet;

import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.booleanAggregate;
import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.intMaxMinAggregate;

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

    @Nullable
    public SearchResultItem calculateScore(Arena arena,
                                           @Nullable DebugRankingFactors rankingFactors,
                                           long combinedId,
                                           QuerySearchTerms searchTerms,
                                           long[] wordFlags,
                                           CodedSequence[] positions)
    {

        CompiledQuery<CodedSequence> positionsQuery = compiledQuery.root.newQuery(positions);

        // If the document is not relevant to the query, abort early to reduce allocations and
        // avoid unnecessary calculations

        CompiledQueryLong wordFlagsQuery = compiledQuery.root.newQuery(wordFlags);
        if (!meetsQueryStrategyRequirements(wordFlagsQuery, queryParams.queryStrategy())) {
            return null;
        }

        boolean allSynthetic = booleanAggregate(wordFlagsQuery, flags -> WordFlags.Synthetic.isPresent((byte) flags));
        int minFlagsCount = intMaxMinAggregate(wordFlagsQuery, flags -> Long.bitCount(flags & 0xff));
        int minPositionsCount = intMaxMinAggregate(positionsQuery, pos -> pos == null ? 0 : pos.valueCount());

        if (minFlagsCount == 0 && !allSynthetic && minPositionsCount == 0) {
            return null;
        }

        long docId = UrlIdCodec.removeRank(combinedId);
        long docMetadata = index.getDocumentMetadata(docId);
        int htmlFeatures = index.getHtmlFeatures(docId);
        int docSize = index.getDocumentSize(docId);
        DocumentSpans spans = index.getDocumentSpans(arena, docId);

        if (rankingFactors != null) {
            rankingFactors.addDocumentFactor("doc.docId", Long.toString(combinedId));
            rankingFactors.addDocumentFactor("doc.combinedId", Long.toString(docId));
        }

        double score = calculateSearchResultValue(
                rankingFactors,
                searchTerms,
                wordFlagsQuery,
                docMetadata,
                htmlFeatures,
                docSize,
                spans,
                positions,
                searchTerms.phraseConstraints,
                rankingContext);

        return new SearchResultItem(combinedId,
                docMetadata,
                htmlFeatures,
                score,
                calculatePositionsMask(positions)
        );
    }

    /** Calculate a bitmask illustrating the intersected positions of the search terms in the document.
     *  This is used in the GUI.
     * */
    private long calculatePositionsMask(CodedSequence[] positions) {
        IntIterator[] iters = new IntIterator[rankingContext.regularMask.cardinality()];
        for (int i = 0, j = 0; i < positions.length; i++) {
            if (rankingContext.regularMask.get(i)) {
                iters[j++] = positions[i].iterator();
            }
        }
        IntIterator intersection = SequenceOperations.findIntersections(iters).intIterator();

        long result = 0;
        int bit = 0;

        while (intersection.hasNext() && bit < 64) {
            bit = (int) (Math.sqrt(intersection.nextInt()));
            result |= 1L << bit;
        }

        return result;
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
                flags -> meetsQueryStrategyRequirements((byte) flags, queryParams.queryStrategy()));
    }

    private boolean meetsQueryStrategyRequirements(byte flags, QueryStrategy queryStrategy) {
        if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SITE) {
            return WordFlags.Site.isPresent(flags);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SUBJECT) {
            return WordFlags.Subjects.isPresent(flags);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_TITLE) {
            return WordFlags.Title.isPresent(flags);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_URL) {
            return WordFlags.UrlPath.isPresent(flags);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_DOMAIN) {
            return WordFlags.UrlDomain.isPresent(flags);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_LINK) {
            return WordFlags.ExternalLink.isPresent(flags);
        }
        return true;
    }



    public double calculateSearchResultValue(DebugRankingFactors rankingFactors,
                                             QuerySearchTerms searchTerms,
                                             CompiledQueryLong wordFlagsQuery,
                                             long documentMetadata,
                                             int features,
                                             int length,
                                             DocumentSpans spans,
                                             CodedSequence[] positions,
                                             PhraseConstraintGroupList constraintGroups,
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

        final int titleLength = Math.max(1, spans.title.length());

        VerbatimMatches verbatimMatches = new VerbatimMatches();

        float verbatimMatchScore = findVerbatimMatches(verbatimMatches, constraintGroups, positions, spans);

        float[] weightedCounts = new float[compiledQuery.size()];
        float keywordMinDistFac = 0;
        if (positions.length > 2) {
            int minDist = constraintGroups.getFullGroup().minDistance(positions);
            if (minDist > 0 && minDist < Integer.MAX_VALUE) {
                if (minDist < 32) {
                    // If min-dist is sufficiently small, we give a tapering reward to the document
                    keywordMinDistFac = 2.0f / (0.1f + (float) Math.sqrt(minDist));
                } else {
                    // if it is too large, we add a mounting penalty
                    keywordMinDistFac = -1.0f * (float) Math.sqrt(minDist);
                }
            }
        }

        int searchableKeywordsCount = 0;
        int unorderedMatchInTitleCount = 0;
        int unorderedMatchInHeadingCount = 0;

        int firstPosition = 1;
        for (int i = 0; i < weightedCounts.length; i++) {
            if (positions[i] != null && ctx.regularMask.get(i)) {
                searchableKeywordsCount ++;

                IntList positionValues = positions[i].values();

                for (int idx = 0; idx < positionValues.size(); idx++) {
                    int pos = positionValues.getInt(idx);
                    firstPosition = Math.max(firstPosition, pos);
                }

                int cnt;
                if ((cnt = spans.title.countIntersections(positionValues.iterator())) != 0) {
                    unorderedMatchInTitleCount++;
                    weightedCounts[i] += 2.5f * cnt;
                }
                if ((cnt = spans.heading.countIntersections(positionValues.iterator())) != 0) {
                    unorderedMatchInHeadingCount++;
                    weightedCounts[i] += 2.5f * cnt;
                }
                if ((cnt = spans.code.countIntersections(positionValues.iterator())) != 0) {
                    weightedCounts[i] += 0.25f * cnt;
                }
                if ((cnt = spans.anchor.countIntersections(positionValues.iterator())) != 0) {
                    weightedCounts[i] += 0.2f * cnt;
                }
                if ((cnt = spans.nav.countIntersections(positionValues.iterator())) != 0) {
                    weightedCounts[i] += 0.1f * cnt;
                }
                if ((cnt = spans.body.countIntersections(positionValues.iterator())) != 0) {
                    weightedCounts[i] += 1.0f * cnt;
                }
            }
        }

        if (!verbatimMatches.get(HtmlTag.TITLE) && searchableKeywordsCount > 2 && unorderedMatchInTitleCount == searchableKeywordsCount) {
            verbatimMatchScore += 2.5f * unorderedMatchInTitleCount;
            verbatimMatchScore += 2.f * unorderedMatchInTitleCount / titleLength;
        }

        if (!verbatimMatches.get(HtmlTag.HEADING) && unorderedMatchInHeadingCount == searchableKeywordsCount) {
            verbatimMatchScore += 2.0f * unorderedMatchInHeadingCount;
        }

        double overallPart = averageSentenceLengthPenalty
                + documentLengthPenalty
                + qualityPenalty
                + rankingBonus
                + topologyBonus
                + temporalBias
                + flagsPenalty;

        double score_firstPosition = rankingParams.tcfFirstPosition * (1.0 / Math.sqrt(firstPosition));

        double score_bM25 = rankingParams.bm25Weight * wordFlagsQuery.root.visit(new Bm25GraphVisitor(rankingParams.bm25Params, weightedCounts, length, ctx));
        double score_bFlags = rankingParams.bm25Weight * wordFlagsQuery.root.visit(new TermFlagsGraphVisitor(rankingParams.bm25Params, wordFlagsQuery.data, weightedCounts, ctx));
        double score_verbatim = rankingParams.tcfVerbatim * verbatimMatchScore;
        double score_proximity = rankingParams.tcfProximity * keywordMinDistFac;

        score_bM25 *= 1.0 / (Math.sqrt(weightedCounts.length + 1));
        score_bFlags *= 1.0 / (Math.sqrt(weightedCounts.length + 1));

        if (rankingFactors != null) {
            rankingFactors.addDocumentFactor("overall.averageSentenceLengthPenalty", Double.toString(averageSentenceLengthPenalty));
            rankingFactors.addDocumentFactor("overall.documentLengthPenalty", Double.toString(documentLengthPenalty));
            rankingFactors.addDocumentFactor("overall.qualityPenalty", Double.toString(qualityPenalty));
            rankingFactors.addDocumentFactor("overall.rankingBonus", Double.toString(rankingBonus));
            rankingFactors.addDocumentFactor("overall.topologyBonus", Double.toString(topologyBonus));
            rankingFactors.addDocumentFactor("overall.temporalBias", Double.toString(temporalBias));
            rankingFactors.addDocumentFactor("overall.flagsPenalty", Double.toString(flagsPenalty));



            rankingFactors.addDocumentFactor("score.bm25-main", Double.toString(score_bM25));
            rankingFactors.addDocumentFactor("score.bm25-flags", Double.toString(score_bFlags));
            rankingFactors.addDocumentFactor("score.verbatim", Double.toString(score_verbatim));
            rankingFactors.addDocumentFactor("score.proximity", Double.toString(score_proximity));
            rankingFactors.addDocumentFactor("score.firstPosition", Double.toString(score_firstPosition));

            rankingFactors.addDocumentFactor("unordered.title", Integer.toString(unorderedMatchInTitleCount));
            rankingFactors.addDocumentFactor("unordered.heading", Integer.toString(unorderedMatchInHeadingCount));

            for (int i = 0; i < searchTerms.termIdsAll.size(); i++) {
                long termId = searchTerms.termIdsAll.at(i);

                rankingFactors.addTermFactor(termId, "factor.weightedCount", Double.toString(weightedCounts[i]));
                var flags = wordFlagsQuery.at(i);

                rankingFactors.addTermFactor(termId, "flags.rawEncoded", Long.toString(flags));

                for (var flag : WordFlags.values()) {
                    if (flag.isPresent((byte) flags)) {
                        rankingFactors.addTermFactor(termId, "flags." + flag.name(), "true");
                    }
                }

                for (HtmlTag tag : HtmlTag.includedTags) {
                    if (verbatimMatches.get(tag)) {
                        rankingFactors.addTermFactor(termId, "verbatim." + tag.name().toLowerCase(), "true");
                    }
                }

                if (positions[i] != null) {
                    rankingFactors.addTermFactor(termId, "positions.all", positions[i].iterator());
                    rankingFactors.addTermFactor(termId, "positions.title", SequenceOperations.findIntersections(spans.title.iterator(), positions[i].iterator()).iterator());
                    rankingFactors.addTermFactor(termId, "positions.heading", SequenceOperations.findIntersections(spans.heading.iterator(), positions[i].iterator()).iterator());
                    rankingFactors.addTermFactor(termId, "positions.anchor", SequenceOperations.findIntersections(spans.anchor.iterator(), positions[i].iterator()).iterator());
                    rankingFactors.addTermFactor(termId, "positions.code", SequenceOperations.findIntersections(spans.code.iterator(), positions[i].iterator()).iterator());
                    rankingFactors.addTermFactor(termId, "positions.nav", SequenceOperations.findIntersections(spans.nav.iterator(), positions[i].iterator()).iterator());
                    rankingFactors.addTermFactor(termId, "positions.body", SequenceOperations.findIntersections(spans.body.iterator(), positions[i].iterator()).iterator());
                    rankingFactors.addTermFactor(termId, "positions.externalLinkText", SequenceOperations.findIntersections(spans.externalLinkText.iterator(), positions[i].iterator()).iterator());
                }

            }
        }

        // Renormalize to 0...15, where 0 is the best possible score;
        // this is a historical artifact of the original ranking function
        double ret = normalize(
                score_firstPosition + score_proximity + score_verbatim
                        + score_bM25
                        + score_bFlags
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

    private float findVerbatimMatches(VerbatimMatches verbatimMatches,
                                      PhraseConstraintGroupList constraints,
                                      CodedSequence[] positions,
                                      DocumentSpans spans) {

        // Calculate a bonus for keyword coherences when large ones exist
        int largestOptional = constraints.getFullGroup().size;
        if (largestOptional < 2) {
            return 0;
        }

        float verbatimMatchScore = 0.f;

        var fullGroup = constraints.getFullGroup();
        for (var tag : HtmlTag.includedTags) {
            if (fullGroup.test(spans.getSpan(tag), positions)) {
                verbatimMatchScore += verbatimMatches.getWeightFull(tag) * fullGroup.size;
                verbatimMatches.set(tag);
            }
        }

        // For optional groups, we scale the score by the size of the group relative to the full group
        for (var optionalGroup : constraints.getOptionalGroups()) {
            int groupSize = optionalGroup.size;
            float sizeScalingFactor = groupSize / (float) largestOptional;

            for (var tag : HtmlTag.includedTags) {
                if (optionalGroup.test(spans.getSpan(tag), positions)) {
                    verbatimMatchScore += verbatimMatches.getWeightPartial(tag) * sizeScalingFactor * groupSize;
                }
            }
        }

        return verbatimMatchScore;
    }

    private static class VerbatimMatches {
        private final BitSet matches;
        private final float[] weights_full;
        private final float[] weights_partial;

        public VerbatimMatches() {
            matches = new BitSet(HtmlTag.includedTags.length);
            weights_full = new float[HtmlTag.includedTags.length];
            weights_partial = new float[HtmlTag.includedTags.length];

            for (int i = 0; i < weights_full.length; i++) {
                weights_full[i] = switch(HtmlTag.includedTags[i]) {
                    case TITLE -> 4.0f;
                    case HEADING -> 1.5f;
                    case ANCHOR -> 0.2f;
                    case NAV -> 0.1f;
                    case CODE -> 0.25f;
                    case EXTERNAL_LINKTEXT -> 1.0f;
                    case BODY -> 1.0f;
                    default -> 0.0f;
                };
            }

            for (int i = 0; i < weights_full.length; i++) {
                weights_partial[i] = switch(HtmlTag.includedTags[i]) {
                    case TITLE -> 1.5f;
                    case HEADING -> 1.f;
                    case ANCHOR -> 0.2f;
                    case NAV -> 0.1f;
                    case CODE -> 0.25f;
                    case EXTERNAL_LINKTEXT -> 1.0f;
                    case BODY -> 0.25f;
                    default -> 0.0f;
                };
            }
        }

        public boolean get(HtmlTag tag) {
            assert !tag.exclude;
            return matches.get(tag.ordinal());
        }

        public void set(HtmlTag tag) {
            assert !tag.exclude;
            matches.set(tag.ordinal());
        }

        public float getWeightFull(HtmlTag tag) {
            assert !tag.exclude;
            return weights_full[tag.ordinal()];
        }
        public float getWeightPartial(HtmlTag tag) {
            assert !tag.exclude;
            return weights_partial[tag.ordinal()];
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

}
