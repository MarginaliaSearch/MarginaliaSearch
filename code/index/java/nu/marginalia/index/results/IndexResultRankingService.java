package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.compiled.CqDataLong;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.debug.DebugRankingFactors;
import nu.marginalia.index.CombinedIndexReader;
import nu.marginalia.index.StatefulIndex;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.TermMetadataList;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.sequence.CodedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.foreign.Arena;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public RankingData prepareRankingData(SearchContext rankingContext, CombinedDocIdList resultIds, @Nullable IndexSearchBudget budget) throws TimeoutException {
        return new RankingData(rankingContext, resultIds, budget);
    }

    public final class RankingData implements AutoCloseable {
        final Arena arena;

        private final TermMetadataList[] termsForDocs;
        private final DocumentSpans[] documentSpans;
        private final long[] flags;
        private final CodedSequence[] positions;
        private final CombinedDocIdList resultIds;
        private AtomicBoolean closed = new AtomicBoolean(false);
        int pos = -1;

        public RankingData(SearchContext rankingContext, CombinedDocIdList resultIds, @Nullable IndexSearchBudget budget) throws TimeoutException {
            this.resultIds = resultIds;
            this.arena = Arena.ofShared();

            final int termCount = rankingContext.termIdsAll.size();

            this.flags = new long[termCount];
            this.positions = new CodedSequence[termCount];

            // Get the current index reader, which is the one we'll use for this calculation,
            // this may change during the calculation, but we don't want to switch over mid-calculation

            final CombinedIndexReader currentIndex = statefulIndex.get();

            // Perform expensive I/O operations

            try {
                this.termsForDocs = currentIndex.getTermMetadata(arena, budget, rankingContext.termIdsAll.array, resultIds);
                this.documentSpans = currentIndex.getDocumentSpans(arena, budget, resultIds);
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
        public long resultId() {
            return resultIds.at(pos);
        }
        public DocumentSpans documentSpans() {
            return documentSpans[pos];
        }

        public boolean next() {
            if (++pos < resultIds.size()) {
                for (int ti = 0; ti < flags.length; ti++) {
                    var tfd = termsForDocs[ti];

                    assert tfd != null : "No term data for term " + ti;

                    flags[ti] = tfd.flag(pos);
                    positions[ti] = tfd.position(pos);
                }
                return true;
            }
            return false;
        }

        public int size() {
            return resultIds.size();
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                arena.close();
            }
        }

    }

    public List<SearchResultItem> rankResults(
            IndexSearchBudget budget,
            SearchContext rankingContext,
            RankingData rankingData,
            boolean exportDebugData)
    {
        IndexResultScoreCalculator resultRanker = new IndexResultScoreCalculator(statefulIndex, domainRankingOverrides, rankingContext);

        List<SearchResultItem> results = new ArrayList<>(rankingData.size());

        // Iterate over documents by their index in the combinedDocIds, as we need the index for the
        // term data arrays as well

        while (rankingData.next() && budget.hasTimeLeft()) {

            // Ignore documents that don't match the mandatory constraints
            if (!rankingContext.phraseConstraints.testMandatory(rankingData.positions())) {
                continue;
            }

            if (!exportDebugData) {
                var score = resultRanker.calculateScore(null, rankingData.resultId(), rankingContext, rankingData.flags(), rankingData.positions(), rankingData.documentSpans());
                if (score != null) {
                    results.add(score);
                }
            }
            else {
                var rankingFactors = new DebugRankingFactors();
                var score = resultRanker.calculateScore( rankingFactors, rankingData.resultId(), rankingContext, rankingData.flags(), rankingData.positions(), rankingData.documentSpans());

                if (score != null) {
                    score.debugRankingFactors = rankingFactors;
                    results.add(score);
                }
            }
        }

        return results;
    }


    public List<RpcDecoratedResultItem> selectBestResults(int limitByDomain,
                                                          int limitTotal,
                                                          SearchContext searchContext,
                                                          List<SearchResultItem> results) throws SQLException {

        var domainCountFilter = new IndexResultDomainDeduplicator(limitByDomain);

        List<SearchResultItem> resultsList = new ArrayList<>(results.size());
        TLongList idsList = new TLongArrayList(limitTotal);

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

            resultsList.clear();
            IndexSearchBudget budget = new IndexSearchBudget(10000);
            try (var data = prepareRankingData(searchContext,  new CombinedDocIdList(combinedIdsList), null)) {
                resultsList.addAll(this.rankResults(
                        budget,
                        searchContext,
                        data,
                        true)
                );
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



}
