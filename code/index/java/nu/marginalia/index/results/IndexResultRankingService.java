package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.CqDataLong;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.debug.DebugRankingFactors;
import nu.marginalia.index.CombinedIndexReader;
import nu.marginalia.index.StatefulIndex;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.index.model.*;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.SequenceOperations;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.foreign.Arena;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.booleanAggregate;
import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.intMaxMinAggregate;

@Singleton
public class IndexResultRankingService {
    private static final Logger logger = LoggerFactory.getLogger(IndexResultRankingService.class);

    private final DocumentDbReader documentDbReader;
    private final StatefulIndex statefulIndex;
    private final DomainRankingOverrides domainRankingOverrides;

    @Inject
    public IndexResultRankingService(DocumentDbReader documentDbReader,
                                     StatefulIndex statefulIndex,
                                     DomainRankingOverrides domainRankingOverrides)
    {
        this.documentDbReader = documentDbReader;
        this.statefulIndex = statefulIndex;
        this.domainRankingOverrides = domainRankingOverrides;
    }

    public RankingData prepareRankingData(SearchContext rankingContext, CombinedDocIdList resultIds) throws TimeoutException {
        return new RankingData(rankingContext, resultIds);
    }

    public final class RankingData implements AutoCloseable {
        final Arena arena;
        private static final DocumentSpans emptySpansInstance = new DocumentSpans();
        private final int numPriorityTerms;

        private final CombinedTermMetadata termMetadata;
        private final DocumentSpans[] documentSpans;
        private final long[] flags;
        private final BitSet priorityTermsPresent;
        private final CodedSequence[] positions;
        private final CombinedDocIdList resultIds;

        private AtomicBoolean closed = new AtomicBoolean(false);
        int pos = -1;

        public RankingData(SearchContext rankingContext, CombinedDocIdList resultIds) throws TimeoutException {
            this.resultIds = resultIds;
            this.arena = Arena.ofShared();

            this.numPriorityTerms = rankingContext.termIdsPriority.size();
            final int termCount = rankingContext.termIdsAll.size();

            this.priorityTermsPresent = new BitSet(rankingContext.termIdsPriority.size());
            this.flags = new long[termCount];
            this.positions = new CodedSequence[termCount];

            // Get the current index reader, which is the one we'll use for this calculation,
            // this may change during the calculation, but we don't want to switch over mid-calculation

            final CombinedIndexReader currentIndex = statefulIndex.get();

            // Perform expensive I/O operations

            try {
                this.termMetadata = currentIndex.getTermMetadata(arena, rankingContext, resultIds);

                @NotNull
                BitSet documentMask = termMetadata.viableDocuments();

                this.documentSpans = currentIndex.getDocumentSpans(arena, rankingContext.budget, resultIds, documentMask);
            }
            catch (TimeoutException|RuntimeException ex) {
                arena.close();
                throw ex;
            }
        }

        public CodedSequence[] positions() {
            return positions;
        }
        public long[] flags() {
            return flags;
        }
        public BitSet priorityTermsPresent() { return priorityTermsPresent; }
        public long resultId() {
            return resultIds.at(pos);
        }
        public DocumentSpans documentSpans() {
            return Objects.requireNonNullElse(documentSpans[pos], emptySpansInstance);
        }

        public boolean next() {
            BitSet viableDocuments = termMetadata.viableDocuments();

            do {
                if (++pos >= resultIds.size()) {
                    return false;
                }
            } while (!viableDocuments.get(pos));


            for (int ti = 0; ti < flags.length; ti++) {
                var tfd = termMetadata.termMetadata(ti);

                assert tfd != null : "No term data for term " + ti;

                flags[ti] = tfd.flag(pos);
                positions[ti] = tfd.position(pos);
            }

            priorityTermsPresent.clear();

            for (int ti = 0; ti < numPriorityTerms; ti++) {
                if (termMetadata.priorityTermsPresent(ti, pos)) {
                    priorityTermsPresent.set(ti);
                }
            }

            return true;
        }

        public int size() {
            return termMetadata.viableDocuments().cardinality();
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                arena.close();
            }
        }

    }

    public List<SearchResultItem> rankResults(
            SearchContext rankingContext,
            RankingData rankingData)
    {
        List<SearchResultItem> results = new ArrayList<>(rankingData.size());
        CombinedIndexReader index = statefulIndex.get();

        // Iterate over documents by their index in the combinedDocIds, as we need the index for the
        // term data arrays as well

        while (rankingData.next() && rankingContext.budget.hasTimeLeft()) {

            // Ignore documents that don't match the mandatory constraints
            if (!rankingContext.phraseConstraints.testMandatory(rankingData.positions())) {
                continue;
            }

            SearchResultItem score = calculateScore(null, index, rankingData.resultId(), rankingContext, rankingData);
            if (score != null) {
                results.add(score);
            }
        }

        return results;
    }

    public List<RpcDecoratedResultItem> selectBestResults(int limitByDomain,
                                                          int limitTotal,
                                                          SearchContext searchContext,
                                                          List<SearchResultItem> results) throws SQLException {


        List<SearchResultItem> resultsList = new ArrayList<>(results.size());
        TLongList idsList = new TLongArrayList(limitTotal);

        IndexResultDomainDeduplicator domainCountFilter = new IndexResultDomainDeduplicator(limitByDomain);
        for (var item : results) {
            if (domainCountFilter.test(item)) {

                if (resultsList.size() < limitTotal) {
                    resultsList.add(item);
                    idsList.add(item.getDocumentId());
                }
                //
                // else { break; } <-- don't add this even though it looks like it should be present!
                //
                // It's important that this filter runs across all results, not just the top N,
                // so we shouldn't break the loop in a putative else-case here!
                //

            }
        }

        // If we're exporting debug data from the ranking, we need to re-run the ranking calculation
        // for the selected results, as this would be comically expensive to do for all the results we
        // discard along the way

        if (searchContext.params.getExportDebugData()) {
            var combinedIdsList = new LongArrayList(resultsList.size());
            for (var item : resultsList) {
                combinedIdsList.add(item.combinedId);
            }
            combinedIdsList.sort(Long::compareTo);

            resultsList.clear();

            // Re-rank the results while gathering debugging data
            try (RankingData rankingData = prepareRankingData(searchContext,  new CombinedDocIdList(combinedIdsList))) {
                CombinedIndexReader index = statefulIndex.get();

                // Iterate over documents by their index in the combinedDocIds, as we need the index for the
                // term data arrays as well

                while (rankingData.next()) {
                    if (!searchContext.phraseConstraints.testMandatory(rankingData.positions())) {
                        continue;
                    }

                    SearchResultItem score = calculateScore(new DebugRankingFactors(), index, rankingData.resultId(), searchContext, rankingData);
                    if (score != null) {
                        resultsList.add(score);
                    }
                }
            }
            catch (TimeoutException ex) {
                // this won't happen since we passed null for budget
            }

        }

        // Fetch the document details for the selected results in one go, from the local document database
        // for this index partition
        Map<Long, DocdbUrlDetail> detailsById = new HashMap<>(idsList.size());
        for (var item : documentDbReader.getUrlDetails(idsList)) {
            detailsById.put(item.urlId(), item);
        }

        List<RpcDecoratedResultItem> resultItems = new ArrayList<>(resultsList.size());
        LongOpenHashSet seenDocumentHashes = new LongOpenHashSet(resultsList.size());

        // Decorate the results with the document details
        for (SearchResultItem result : resultsList) {
            final long id = result.getDocumentId();
            final DocdbUrlDetail docData = detailsById.get(id);

            if (docData == null) {
                logger.warn("No document data for id {}", id);
                continue;
            }

            // Filter out duplicates by content
            if (!seenDocumentHashes.add(docData.dataHash())) {
                continue;
            }

            var rawItem = RpcRawResultItem.newBuilder();

            rawItem.setCombinedId(result.combinedId);
            rawItem.setHtmlFeatures(result.htmlFeatures);
            rawItem.setEncodedDocMetadata(result.encodedDocMetadata);
            rawItem.setHasPriorityTerms(result.hasPrioTerm);

            for (var score : result.keywordScores) {
                rawItem.addKeywordScores(
                        RpcResultKeywordScore.newBuilder()
                                .setFlags(score.flags)
                                .setPositions(score.positionCount)
                                .setKeyword(score.keyword)
                );
            }

            var decoratedBuilder = RpcDecoratedResultItem.newBuilder()
                    .setDataHash(docData.dataHash())
                    .setDescription(docData.description())
                    .setFeatures(docData.features())
                    .setFormat(docData.format())
                    .setRankingScore(result.getScore())
                    .setTitle(docData.title())
                    .setUrl(docData.url().toString())
                    .setUrlQuality(docData.urlQuality())
                    .setWordsTotal(docData.wordsTotal())
                    .setBestPositions(result.getBestPositions())
                    .setResultsFromDomain(domainCountFilter.getCount(result))
                    .setRawItem(rawItem);

            if (docData.pubYear() != null) {
                decoratedBuilder.setPubYear(docData.pubYear());
            }

            if (result.debugRankingFactors != null) {
                var debugFactors = result.debugRankingFactors;
                var detailsBuilder = RpcResultRankingDetails.newBuilder();
                var documentOutputs = RpcResultDocumentRankingOutputs.newBuilder();

                for (var factor : debugFactors.getDocumentFactors()) {
                    documentOutputs.addFactor(factor.factor());
                    documentOutputs.addValue(factor.value());
                }

                detailsBuilder.setDocumentOutputs(documentOutputs);

                var termOutputs = RpcResultTermRankingOutputs.newBuilder();

                CqDataLong termIds = searchContext.compiledQueryIds.data;

                for (var entry : debugFactors.getTermFactors()) {
                    String term = "[ERROR IN LOOKUP]";

                    // CURSED: This is a linear search, but the number of terms is small, and it's in a debug path
                    for (int i = 0; i < termIds.size(); i++) {
                        if (termIds.get(i) == entry.termId()) {
                            term = searchContext.compiledQuery.at(i);
                            break;
                        }
                    }

                    termOutputs
                            .addTermId(entry.termId())
                            .addTerm(term)
                            .addFactor(entry.factor())
                            .addValue(entry.value());
                }

                detailsBuilder.setTermOutputs(termOutputs);
                decoratedBuilder.setRankingDetails(detailsBuilder);
            }

            resultItems.add(decoratedBuilder.build());
        }

        return resultItems;
    }



    @Nullable
    public SearchResultItem calculateScore(@Nullable DebugRankingFactors debugRankingFactors,
                                           CombinedIndexReader index,
                                           long combinedId,
                                           SearchContext rankingContext,
                                           IndexResultRankingService.RankingData rankingData)
    {
        long[] wordFlags = rankingData.flags();
        CodedSequence[] positions = rankingData.positions();
        DocumentSpans spans = rankingData.documentSpans();

        QueryParams queryParams = rankingContext.queryParams;
        CompiledQuery<String> compiledQuery = rankingContext.compiledQuery;

        CompiledQuery<CodedSequence> positionsQuery = compiledQuery.forData(positions);

        // If the document is not relevant to the query, abort early to reduce allocations and
        // avoid unnecessary calculations

        CompiledQueryLong wordFlagsQuery = compiledQuery.forData(wordFlags);
        if (!meetsQueryStrategyRequirements(wordFlagsQuery, queryParams)) {
            return null;
        }

        boolean allSynthetic = booleanAggregate(wordFlagsQuery, flags -> WordFlags.Synthetic.isPresent((byte) flags));
        int minFlagsCount = intMaxMinAggregate(wordFlagsQuery, flags -> Long.bitCount(flags & 0xff));
        int minPositionsCount = intMaxMinAggregate(positionsQuery, pos -> pos == null ? 0 : pos.valueCount());

        if (minFlagsCount == 0 && !allSynthetic && minPositionsCount == 0) {
            return null;
        }

        long docId = UrlIdCodec.removeRank(combinedId);
        long docMetadata = index.getDocumentMetadata(combinedId);
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

        var params = rankingContext.params;

        double documentBonus = calculateDocumentBonus(docMetadata, htmlFeatures, docSize, params, debugRankingFactors);

        VerbatimMatches verbatimMatches = new VerbatimMatches(decodedPositions, rankingContext.phraseConstraints, spans);
        UnorderedMatches unorderedMatches = new UnorderedMatches(decodedPositions, compiledQuery, rankingContext.regularMask, spans);

        float proximitiyFac = getProximitiyFac(decodedPositions, rankingContext.phraseConstraints, verbatimMatches, unorderedMatches, spans);

        double score_firstPosition = params.getTcfFirstPositionWeight() * (1.0 / Math.sqrt(unorderedMatches.firstPosition));
        double score_verbatim = params.getTcfVerbatimWeight() * verbatimMatches.getScore();
        double score_proximity = params.getTcfProximityWeight() * proximitiyFac;
        double score_bM25 = params.getBm25Weight()
                * CompiledQueryAggregates.intMaxSumAggregateOfIndexes(wordFlagsQuery, new Bm25GraphVisitor(params.getBm25K(), params.getBm25B(), unorderedMatches.getWeightedCounts(), docSize, rankingContext))
                / (Math.sqrt(unorderedMatches.searchableKeywordCount + 1));
        double score_bFlags = params.getBm25Weight()
                * CompiledQueryAggregates.intMaxSumAggregateOfIndexes(wordFlagsQuery, new TermFlagsGraphVisitor(params.getBm25K(), wordFlagsQuery.data, unorderedMatches.getWeightedCounts(), rankingContext))
                / (Math.sqrt(unorderedMatches.searchableKeywordCount + 1));

        double rankingAdjustment = domainRankingOverrides.getRankingFactor(UrlIdCodec.getDomainId(combinedId));

        double priorityTermAdjustment = 0.;
        BitSet priorityTermsPresent = rankingData.priorityTermsPresent();

        for (int i = 0; i < rankingContext.termIdsPriority.size(); i++) {
            if (priorityTermsPresent.get(i))
                priorityTermAdjustment += rankingContext.termIdsPriorityWeights.getFloat(i);
        }

        priorityTermAdjustment += rankingContext.priorityDomainIds.getOrDefault(UrlIdCodec.getDomainId(combinedId), 0.f);

        double score = normalize(
                rankingAdjustment * (score_firstPosition + score_proximity + score_verbatim + score_bM25 + score_bFlags) * Math.exp(priorityTermAdjustment/5),
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

        SearchResultItem ret = new SearchResultItem(combinedId,
                docMetadata,
                htmlFeatures,
                score,
                calculatePositionsMask(decodedPositions, rankingContext.phraseConstraints)
        );

        if (null != debugRankingFactors) {
            ret.debugRankingFactors = debugRankingFactors;
        }

        return ret;
    }

    private boolean meetsQueryStrategyRequirements(CompiledQueryLong queryGraphScores,
                                                   QueryParams queryParams)
    {
        QueryStrategy queryStrategy = queryParams.queryStrategy();
        if (queryStrategy == QueryStrategy.AUTO ||
                queryStrategy == QueryStrategy.SENTENCE ||
                queryStrategy == QueryStrategy.TOPIC) {
            return true;
        }

        return booleanAggregate(queryGraphScores,
                flags -> meetsQueryStrategyRequirements((byte) flags, queryStrategy));
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

        double averageSentenceLengthPenalty
                = (asl >= rankingParams.getShortSentenceThreshold() ? 0 : -rankingParams.getShortSentencePenalty());

        final double qualityPenalty = calculateQualityPenalty(size, quality, rankingParams);
        final double rankingBonus = (255. - rank) * rankingParams.getDomainRankBonus();
        final double topologyBonus = Math.log(1 + topology);
        final double documentLengthPenalty
                = length > rankingParams.getShortDocumentThreshold() ? 0 : -rankingParams.getShortDocumentPenalty();
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
            var fullGroup = constraintGroups.getFullGroup();

            int minDist = fullGroup.minDistance(positions);
            if (minDist > 0 && minDist < Integer.MAX_VALUE) {
                if (minDist < fullGroup.size + 8) {
                    // If min-dist is sufficiently small, we give a tapering reward to the document
                    proximitiyFac = 2.0f / (0.1f + (float) Math.sqrt(minDist));
                }
            }
        }


        // Give bonus proximity score if all keywords are in the title
        if (!verbatimMatches.get(HtmlTag.TITLE)
                && unorderedMatches.searchableKeywordCount >= 2
                && unorderedMatches.getObservationCount(HtmlTag.TITLE) == unorderedMatches.searchableKeywordCount) {
            proximitiyFac += unorderedMatches.getObservationCount(HtmlTag.TITLE)
                    * (2.5f + 2.f / Math.max(1, spans.title.length()));
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
                    case TITLE -> 2.5f;
                    case HEADING -> 1.f;
                    case ANCHOR -> 0.2f;
                    case NAV -> 0.1f;
                    case CODE -> 0.25f;
                    case EXTERNAL_LINKTEXT -> 2.0f;
                    case BODY -> 0.5f;
                    default -> 0.0f;
                };
            }
        }

        public VerbatimMatches(IntList[] positions, PhraseConstraintGroupList constraints, DocumentSpans spans) {
            matches = new BitSet(HtmlTag.includedTags.length);

            PhraseConstraintGroupList.PhraseConstraintGroup fullGroup = constraints.getFullGroup();
            IntList fullGroupIntersections = fullGroup.findIntersections(positions);

            if (fullGroup.size == 1) {
                var titleSpan = spans.getSpan(HtmlTag.TITLE);
                if (titleSpan.length() == fullGroup.size
                        && titleSpan.containsRange(fullGroupIntersections, fullGroup.size))
                {
                    score += 4; // If the title is a single word and the same as the query, we give it a verbatim bonus
                }

                score += 2 * spans
                            .getSpan(HtmlTag.EXTERNAL_LINKTEXT)
                            .containsRangeExact(fullGroupIntersections, fullGroup.size);

                return;
            }


            /**
             *  FULL GROUP MATCHING
             */


            if (!fullGroupIntersections.isEmpty()) {
                int totalFullCnts = 0;

                // Capture full query matches
                for (var tag : HtmlTag.includedTags) {
                    int cnts = spans.getSpan(tag).countRangeMatches(fullGroupIntersections, fullGroup.size);
                    if (cnts > 0) {
                        matches.set(tag.ordinal());
                        score += (float) (weights_full[tag.ordinal()] * fullGroup.size * (1 + Math.log(2 + cnts)));
                        totalFullCnts += cnts;
                    }
                }

                // Handle matches that span multiple tags; treat them as BODY matches
                if (totalFullCnts != fullGroupIntersections.size()) {
                    int mixedCnts = fullGroupIntersections.size() - totalFullCnts;
                    score += (float) (weights_full[HtmlTag.BODY.ordinal()] * fullGroup.size * (1 + Math.log(2 + mixedCnts)));
                }
            }

            /**
             *  OPTIONAL GROUP MATCHING
             */

            // For optional groups, we scale the score by the size of the group relative to the full group
            for (PhraseConstraintGroupList.PhraseConstraintGroup optionalGroup : constraints.getOptionalGroups()) {
                float sizeScalingFactor = (float) Math.sqrt(optionalGroup.size / (float) fullGroup.size);

                IntList intersections = optionalGroup.findIntersections(positions);

                if (intersections.isEmpty())
                    continue;

                int totalCnts = 0;
                for (var tag : HtmlTag.includedTags) {
                    int cnts =  spans.getSpan(tag).countRangeMatches(intersections, optionalGroup.size);
                    if (cnts == 0) continue;

                    score += (float) (weights_partial[tag.ordinal()] * optionalGroup.size * sizeScalingFactor * (1 + Math.log(2 + cnts)));
                    totalCnts += cnts;
                }

                // Handle matches that span multiple tags; treat them as BODY matches
                if (totalCnts != intersections.size()) {
                    int mixedCnts = intersections.size() - totalCnts;
                    score += (float) (weights_partial[HtmlTag.BODY.ordinal()] * optionalGroup.size * sizeScalingFactor * (1 + Math.log(2 + mixedCnts)));
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
                penalty += 5.0;
            else if (DocumentMetadata.hasFlags(featureFlags, HtmlFeature.LONG_URL.getFeatureBit()))
                penalty += 5.0;

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
            penalty += 5 * largeSiteFactor;

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
