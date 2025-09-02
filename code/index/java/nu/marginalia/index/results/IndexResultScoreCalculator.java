package nu.marginalia.index.results;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.debug.DebugRankingFactors;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.reverse.query.limit.QueryStrategy;
import nu.marginalia.index.results.model.PhraseConstraintGroupList;
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
import java.util.BitSet;

import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.booleanAggregate;
import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.intMaxMinAggregate;

/** This class is responsible for calculating the score of a search result.
 * It holds the data required to perform the scoring, as there is strong
 * reasons to cache this data, and performs the calculations */
public class IndexResultScoreCalculator {
    private final CombinedIndexReader index;
    private final QueryParams queryParams;

    private final DomainRankingOverrides domainRankingOverrides;
    private final SearchContext rankingContext;
    private final CompiledQuery<String> compiledQuery;

    public IndexResultScoreCalculator(StatefulIndex statefulIndex,
                                      DomainRankingOverrides domainRankingOverrides,
                                      SearchContext rankingContext)
    {
        this.index = statefulIndex.get();
        this.domainRankingOverrides = domainRankingOverrides;
        this.rankingContext = rankingContext;

        this.queryParams = rankingContext.queryParams;
        this.compiledQuery = rankingContext.compiledQuery;
    }

    @Nullable
    public SearchResultItem calculateScore(@Nullable DebugRankingFactors debugRankingFactors,
                                           long combinedId,
                                           SearchContext rankingContext,
                                           long[] wordFlags,
                                           CodedSequence[] positions,
                                           DocumentSpans spans)
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
        if (docSize <= 0) docSize = 5000;

        if (debugRankingFactors != null) {
            debugRankingFactors.addDocumentFactor("doc.docId", Long.toString(combinedId));
            debugRankingFactors.addDocumentFactor("doc.combinedId", Long.toString(docId));
        }

        // Decode the coded positions lists into plain IntLists as at this point we will be
        // going over them multiple times
        IntList[] decodedPositions = new IntList[positions.length];
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] != null) {
                decodedPositions[i] = positions[i].values();
            }
            else {
                decodedPositions[i] = IntList.of();
            }
        }

        var params = this.rankingContext.params;

        double documentBonus = calculateDocumentBonus(docMetadata, htmlFeatures, docSize, params, debugRankingFactors);

        VerbatimMatches verbatimMatches = new VerbatimMatches(decodedPositions, rankingContext.phraseConstraints, spans);
        UnorderedMatches unorderedMatches = new UnorderedMatches(decodedPositions, compiledQuery, this.rankingContext.regularMask, spans);

        float proximitiyFac = getProximitiyFac(decodedPositions, rankingContext.phraseConstraints, verbatimMatches, unorderedMatches, spans);

        double score_firstPosition = params.getTcfFirstPositionWeight() * (1.0 / Math.sqrt(unorderedMatches.firstPosition));
        double score_verbatim = params.getTcfVerbatimWeight() * verbatimMatches.getScore();
        double score_proximity = params.getTcfProximityWeight() * proximitiyFac;
        double score_bM25 = params.getBm25Weight()
                * wordFlagsQuery.root.visit(new Bm25GraphVisitor(params.getBm25K(), params.getBm25B(), unorderedMatches.getWeightedCounts(), docSize, this.rankingContext))
                / (Math.sqrt(unorderedMatches.searchableKeywordCount + 1));
        double score_bFlags = params.getBm25Weight()
                * wordFlagsQuery.root.visit(new TermFlagsGraphVisitor(params.getBm25K(), wordFlagsQuery.data, unorderedMatches.getWeightedCounts(), this.rankingContext))
                / (Math.sqrt(unorderedMatches.searchableKeywordCount + 1));

        double rankingAdjustment = domainRankingOverrides.getRankingFactor(UrlIdCodec.getDomainId(combinedId));

        double score = normalize(
                rankingAdjustment * (score_firstPosition + score_proximity + score_verbatim + score_bM25 + score_bFlags),
                -Math.min(0, documentBonus) // The magnitude of documentBonus, if it is negative; otherwise 0
        );

        if (Double.isNaN(score)) { // This should never happen but if it does, we want to know about it
            if (getClass().desiredAssertionStatus()) {
                throw new IllegalStateException("NaN in result value calculation");
            }
            score = Double.MAX_VALUE;
        }

        // Capture ranking factors for debugging
        if (debugRankingFactors != null) {
            debugRankingFactors.addDocumentFactor("score.bm25-main", Double.toString(score_bM25));
            debugRankingFactors.addDocumentFactor("score.bm25-flags", Double.toString(score_bFlags));
            debugRankingFactors.addDocumentFactor("score.verbatim", Double.toString(score_verbatim));
            debugRankingFactors.addDocumentFactor("score.proximity", Double.toString(score_proximity));
            debugRankingFactors.addDocumentFactor("score.firstPosition", Double.toString(score_firstPosition));

            for (int i = 0; i < rankingContext.termIdsAll.size(); i++) {
                long termId = rankingContext.termIdsAll.at(i);

                var flags = wordFlagsQuery.at(i);

                debugRankingFactors.addTermFactor(termId, "flags.rawEncoded", Long.toString(flags));

                for (var flag : WordFlags.values()) {
                    if (flag.isPresent((byte) flags)) {
                        debugRankingFactors.addTermFactor(termId, "flags." + flag.name(), "true");
                    }
                }

                for (HtmlTag tag : HtmlTag.includedTags) {
                    if (verbatimMatches.get(tag)) {
                        debugRankingFactors.addTermFactor(termId, "verbatim." + tag.name().toLowerCase(), "true");
                    }
                }

                if (positions[i] != null) {
                    debugRankingFactors.addTermFactor(termId, "positions.all", positions[i].iterator());
                    debugRankingFactors.addTermFactor(termId, "positions.title", SequenceOperations.findIntersections(spans.title.positionValues(), decodedPositions[i]).iterator());
                    debugRankingFactors.addTermFactor(termId, "positions.heading", SequenceOperations.findIntersections(spans.heading.positionValues(), decodedPositions[i]).iterator());
                    debugRankingFactors.addTermFactor(termId, "positions.anchor", SequenceOperations.findIntersections(spans.anchor.positionValues(), decodedPositions[i]).iterator());
                    debugRankingFactors.addTermFactor(termId, "positions.code", SequenceOperations.findIntersections(spans.code.positionValues(), decodedPositions[i]).iterator());
                    debugRankingFactors.addTermFactor(termId, "positions.nav", SequenceOperations.findIntersections(spans.nav.positionValues(), decodedPositions[i]).iterator());
                    debugRankingFactors.addTermFactor(termId, "positions.body", SequenceOperations.findIntersections(spans.body.positionValues(), decodedPositions[i]).iterator());
                    debugRankingFactors.addTermFactor(termId, "positions.externalLinkText", SequenceOperations.findIntersections(spans.externalLinkText.positionValues(), decodedPositions[i]).iterator());
                }
            }
        }

        return new SearchResultItem(combinedId,
                docMetadata,
                htmlFeatures,
                score,
                calculatePositionsMask(decodedPositions, rankingContext.phraseConstraints)
        );
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

    /** Calculate a bitmask illustrating the intersected positions of the search terms in the document.
     *  This is used in the GUI.
     * */
    private long calculatePositionsMask(IntList[] positions, PhraseConstraintGroupList phraseConstraints) {

        long result = 0;
        int bit = 0;

        IntIterator intersection = phraseConstraints.getFullGroup().findIntersections(positions, 64).intIterator();

        while (intersection.hasNext() && bit < 64) {
            bit = (int) (Math.sqrt(intersection.nextInt()));
            result |= 1L << bit;
        }

        return result;
    }


    private double calculateDocumentBonus(long documentMetadata,
                                          int features,
                                          int length,
                                          RpcResultRankingParameters rankingParams,
                                          @Nullable DebugRankingFactors debugRankingFactors) {

        if (rankingParams.getDisablePenalties()) {
            return 0.;
        }

        int rank = DocumentMetadata.decodeRank(documentMetadata);
        int asl = DocumentMetadata.decodeAvgSentenceLength(documentMetadata);
        int quality = DocumentMetadata.decodeQuality(documentMetadata);
        int size = DocumentMetadata.decodeSize(documentMetadata);
        int flagsPenalty = flagsPenalty(features, documentMetadata & 0xFF, size);
        int topology = DocumentMetadata.decodeTopology(documentMetadata);
        int year = DocumentMetadata.decodeYear(documentMetadata);

        double averageSentenceLengthPenalty = (asl >= rankingParams.getShortSentenceThreshold() ? 0 : -rankingParams.getShortSentencePenalty());

        final double qualityPenalty = calculateQualityPenalty(size, quality, rankingParams);
        final double rankingBonus = (255. - rank) * rankingParams.getDomainRankBonus();
        final double topologyBonus = Math.log(1 + topology);
        final double documentLengthPenalty = length > rankingParams.getShortDocumentThreshold() ? 0 : -rankingParams.getShortDocumentPenalty();
        final double temporalBias;

        if (rankingParams.getTemporalBias().getBias() == RpcTemporalBias.Bias.RECENT) {
            temporalBias = - Math.abs(year - PubDate.MAX_YEAR) * rankingParams.getTemporalBiasWeight();
        } else if (rankingParams.getTemporalBias().getBias() == RpcTemporalBias.Bias.OLD) {
            temporalBias = - Math.abs(year - PubDate.MIN_YEAR) * rankingParams.getTemporalBiasWeight();
        } else {
            temporalBias = 0;
        }

        if (debugRankingFactors != null) {
            debugRankingFactors.addDocumentFactor("documentBonus.averageSentenceLengthPenalty", Double.toString(averageSentenceLengthPenalty));
            debugRankingFactors.addDocumentFactor("documentBonus.documentLengthPenalty", Double.toString(documentLengthPenalty));
            debugRankingFactors.addDocumentFactor("documentBonus.qualityPenalty", Double.toString(qualityPenalty));
            debugRankingFactors.addDocumentFactor("documentBonus.rankingBonus", Double.toString(rankingBonus));
            debugRankingFactors.addDocumentFactor("documentBonus.topologyBonus", Double.toString(topologyBonus));
            debugRankingFactors.addDocumentFactor("documentBonus.temporalBias", Double.toString(temporalBias));
            debugRankingFactors.addDocumentFactor("documentBonus.flagsPenalty", Double.toString(flagsPenalty));
        }

        return averageSentenceLengthPenalty
                + documentLengthPenalty
                + qualityPenalty
                + rankingBonus
                + topologyBonus
                + temporalBias
                + flagsPenalty;
    }

    /** Calculate the proximity factor for the document.
     * <p></p>
     * The proximity factor is a bonus based on how close the search terms are to each other in the document
     * that turns into a penalty if the distance is too large.
     * */
    private static float getProximitiyFac(IntList[] positions,
                                          PhraseConstraintGroupList constraintGroups,
                                          VerbatimMatches verbatimMatches,
                                          UnorderedMatches unorderedMatches,
                                          DocumentSpans spans
                                          ) {
        float proximitiyFac = 0;

        if (positions.length > 2) {
            int minDist = constraintGroups.getFullGroup().minDistance(positions);
            if (minDist > 0 && minDist < Integer.MAX_VALUE) {
                if (minDist < 32) {
                    // If min-dist is sufficiently small, we give a tapering reward to the document
                    proximitiyFac = 2.0f / (0.1f + (float) Math.sqrt(minDist));
                } else {
                    // if it is too large, we add a mounting penalty
                    proximitiyFac = -1.0f * (float) Math.sqrt(minDist);
                }
            }
        }


        // Give bonus proximity score if all keywords are in the title
        if (!verbatimMatches.get(HtmlTag.TITLE) && unorderedMatches.searchableKeywordCount > 2 && unorderedMatches.getObservationCount(HtmlTag.TITLE) == unorderedMatches.searchableKeywordCount) {
            proximitiyFac += unorderedMatches.getObservationCount(HtmlTag.TITLE) * (2.5f + 2.f / Math.max(1, spans.title.length()));
        }
        // Give bonus proximity score if all keywords are in a heading
        if (spans.heading.length() < 64 &&
                ! verbatimMatches.get(HtmlTag.HEADING)
                && unorderedMatches.getObservationCount(HtmlTag.HEADING) == unorderedMatches.searchableKeywordCount)
        {
            proximitiyFac += 1.0f * unorderedMatches.getObservationCount(HtmlTag.HEADING);
        }

        return proximitiyFac;
    }

    /** A helper class for capturing the verbatim phrase matches in the document */
    private static class VerbatimMatches {
        private final BitSet matches;
        private float score = 0.f;

        private static final float[] weights_full;
        private static final float[] weights_partial;

        static {
            weights_full = new float[HtmlTag.includedTags.length];
            weights_partial = new float[HtmlTag.includedTags.length];

            for (int i = 0; i < weights_full.length; i++) {
                weights_full[i] = switch(HtmlTag.includedTags[i]) {
                    case TITLE -> 4.0f;
                    case HEADING -> 1.5f;
                    case ANCHOR -> 0.2f;
                    case NAV -> 0.1f;
                    case CODE -> 0.25f;
                    case EXTERNAL_LINKTEXT -> 3.0f;
                    case BODY -> 1.0f;
                    default -> 0.0f;
                };
            }

            for (int i = 0; i < weights_partial.length; i++) {
                weights_partial[i] = switch(HtmlTag.includedTags[i]) {
                    case TITLE -> 1.5f;
                    case HEADING -> 1.f;
                    case ANCHOR -> 0.2f;
                    case NAV -> 0.1f;
                    case CODE -> 0.25f;
                    case EXTERNAL_LINKTEXT -> 2.0f;
                    case BODY -> 0.25f;
                    default -> 0.0f;
                };
            }
        }

        public VerbatimMatches(IntList[] positions, PhraseConstraintGroupList constraints, DocumentSpans spans) {
            matches = new BitSet(HtmlTag.includedTags.length);

            var fullGroup = constraints.getFullGroup();
            IntList fullGroupIntersections = fullGroup.findIntersections(positions);

            int largestOptional = constraints.getFullGroup().size;
            if (largestOptional < 2) {
                var titleSpan = spans.getSpan(HtmlTag.TITLE);
                if (titleSpan.length() == fullGroup.size
                    && titleSpan.containsRange(fullGroupIntersections, fullGroup.size))
                {
                    score += 4; // If the title is a single word and the same as the query, we give it a verbatim bonus
                }

                var extLinkSpan = spans.getSpan(HtmlTag.EXTERNAL_LINKTEXT);
                if (extLinkSpan.length() >= fullGroup.size) {
                    int cnt = extLinkSpan.containsRangeExact(fullGroupIntersections, fullGroup.size);
                    if (cnt > 0) {
                        score += 2 * cnt;
                    }
                }

                return;
            }

            // Capture full query matches
            for (var tag : HtmlTag.includedTags) {
                int cnts =  spans.getSpan(tag).countRangeMatches(fullGroupIntersections, fullGroup.size);
                if (cnts > 0) {
                    matches.set(tag.ordinal());
                    score += (float) (weights_full[tag.ordinal()] * fullGroup.size + (1 + Math.log(2 + cnts)));
                }
            }

            // Bonus if there's a perfect match with an atag span
            var extLinkSpan = spans.getSpan(HtmlTag.EXTERNAL_LINKTEXT);
            if (extLinkSpan.length() >= fullGroup.size) {
                int cnt = extLinkSpan.containsRangeExact(fullGroupIntersections, fullGroup.size);
                score += 2*cnt;
            }

            // For optional groups, we scale the score by the size of the group relative to the full group
            for (var optionalGroup : constraints.getOptionalGroups()) {
                int groupSize = optionalGroup.size;
                float sizeScalingFactor = groupSize / (float) largestOptional;

                IntList intersections = optionalGroup.findIntersections(positions);

                for (var tag : HtmlTag.includedTags) {
                    int cnts =  spans.getSpan(tag).countRangeMatches(intersections, fullGroup.size);
                    if (cnts > 0) {
                        score += (float) (weights_partial[tag.ordinal()] * optionalGroup.size * sizeScalingFactor * (1 + Math.log(2 + cnts)));
                    }
                }
            }
        }

        public boolean get(HtmlTag tag) {
            assert !tag.exclude;
            return matches.get(tag.ordinal());
        }

        public float getScore() {
            return score;
        }
    }

    /** A helper class for capturing the counts of unordered matches in the document */
    private static class UnorderedMatches {
        private final int[] observationsByTag;
        private final float[] valuesByWordIdx;
        private static final float[] weights;

        private int firstPosition = 1;
        private int searchableKeywordCount = 0;
        static {
            weights = new float[HtmlTag.includedTags.length];

            for (int i = 0; i < weights.length; i++) {
                weights[i] = switch(HtmlTag.includedTags[i]) {
                    case TITLE -> 2.5f;
                    case HEADING -> 1.25f;
                    case ANCHOR -> 0.2f;
                    case NAV -> 0.1f;
                    case CODE -> 0.25f;
                    case BODY -> 1.0f;
                    case EXTERNAL_LINKTEXT -> 1.5f;
                    default -> 0.0f;
                };
            }
        }

        public UnorderedMatches(IntList[] positions, CompiledQuery<String> compiledQuery,
                                BitSet regularMask,
                                DocumentSpans spans) {
            observationsByTag = new int[HtmlTag.includedTags.length];
            valuesByWordIdx = new float[compiledQuery.size()];

            for (int i = 0; i < compiledQuery.size(); i++) {

                if (!regularMask.get(i))
                    continue;

                if (positions[i] == null || positions[i].isEmpty()) {
                    firstPosition = Integer.MAX_VALUE;
                    continue;
                }

                firstPosition = Math.max(firstPosition, positions[i].getInt(0));
                searchableKeywordCount ++;

                for (var tag : HtmlTag.includedTags) {
                    int cnt = spans.getSpan(tag).countIntersections(positions[i]);
                    observationsByTag[tag.ordinal()] += cnt;
                    valuesByWordIdx[i] += cnt * weights[tag.ordinal()];
                }
            }
        }

        public int getObservationCount(HtmlTag tag) {
            return observationsByTag[tag.ordinal()];
        }

        public float[] getWeightedCounts() {
            return valuesByWordIdx;
        }

        public int size() {
            return valuesByWordIdx.length;
        }
    }


    private double calculateQualityPenalty(int size, int quality, RpcResultRankingParameters rankingParams) {
        if (size < 400) {
            if (quality < 5)
                return 0;
            return -quality * rankingParams.getQualityPenalty();
        }
        else {
            return -quality * rankingParams.getQualityPenalty() * 20;
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

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.ADVERTISEMENT.getFeatureBit()))
            penalty += 7.5 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.CONSENT.getFeatureBit()))
            penalty += 2.5 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.POPOVER.getFeatureBit()))
            penalty += 2.5 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.TRACKING_ADTECH.getFeatureBit()))
            penalty += 5.0 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.AFFILIATE_LINK.getFeatureBit()))
            penalty += 5.0 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.COOKIES.getFeatureBit()))
            penalty += 2.5 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.TRACKING.getFeatureBit()))
            penalty += 2.5 * largeSiteFactor;

        if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.SHORT_DOCUMENT.getFeatureBit()))
            penalty += 2.5  * largeSiteFactor;

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

