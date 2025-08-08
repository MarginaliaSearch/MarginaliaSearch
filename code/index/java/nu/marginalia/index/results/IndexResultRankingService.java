package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TObjectLongHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CqDataLong;
import nu.marginalia.api.searchquery.model.query.SearchPhraseConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.debug.DebugRankingFactors;
import nu.marginalia.index.forward.spans.DocumentSpans;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.ResultRankingContext;
import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.index.query.IndexSearchBudget;
import nu.marginalia.index.results.model.PhraseConstraintGroupList;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.results.model.ids.TermIdList;
import nu.marginalia.index.results.model.ids.TermMetadataList;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.sequence.CodedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<SearchResultItem> rankResults(
            ResultRankingContext rankingContext,
            IndexSearchBudget budget,
            CombinedDocIdList resultIds,
            boolean exportDebugData)
    {
        if (resultIds.isEmpty())
            return List.of();

        IndexResultScoreCalculator resultRanker = new IndexResultScoreCalculator(statefulIndex, domainRankingOverrides, rankingContext);

        List<SearchResultItem> results = new ArrayList<>(resultIds.size());

        // Get the current index reader, which is the one we'll use for this calculation,
        // this may change during the calculation, but we don't want to switch over mid-calculation
        final CombinedIndexReader currentIndex = statefulIndex.get();

        final QuerySearchTerms searchTerms = getSearchTerms(rankingContext.compiledQuery, rankingContext.searchQuery);
        final int termCount = searchTerms.termIdsAll.size();

        // We use an arena for the position and spans data to limit gc pressure
        try (var arena = Arena.ofConfined()) {

            TermMetadataList[] termsForDocs = new TermMetadataList[termCount];
            for (int ti = 0; ti < termCount; ti++) {
                termsForDocs[ti] = currentIndex.getTermMetadata(arena, searchTerms.termIdsAll.at(ti), resultIds);
            }

            // Data for the document.  We arrange this in arrays outside the calculation function to avoid
            // hash lookups in the inner loop, as it's hot code, and we don't want unnecessary cpu cache
            // thrashing in there; out here we can rely on implicit array ordering to match up the data.

            long[] flags = new long[termCount];
            CodedSequence[] positions = new CodedSequence[termCount];
            DocumentSpans[] documentSpans = currentIndex.getDocumentSpans(arena, resultIds);

            // Iterate over documents by their index in the combinedDocIds, as we need the index for the
            // term data arrays as well

            for (int i = 0; i < resultIds.size() && budget.hasTimeLeft() && !Thread.interrupted(); i++) {

                // Prepare term-level data for the document
                for (int ti = 0; ti < flags.length; ti++) {
                    var tfd = termsForDocs[ti];

                    assert tfd != null : "No term data for term " + ti;

                    flags[ti] = tfd.flag(i);
                    positions[ti] = tfd.position(i);
                }

                // Ignore documents that don't match the mandatory constraints
                if (!searchTerms.phraseConstraints.testMandatory(positions)) {
                    continue;
                }

                if (!exportDebugData) {
                    var score = resultRanker.calculateScore(null, resultIds.at(i), searchTerms, flags, positions, documentSpans[i]);
                    if (score != null) {
                        results.add(score);
                    }
                }
                else {
                    var rankingFactors = new DebugRankingFactors();
                    var score = resultRanker.calculateScore( rankingFactors, resultIds.at(i), searchTerms, flags, positions, documentSpans[i]);

                    if (score != null) {
                        score.debugRankingFactors = rankingFactors;
                        results.add(score);
                    }
                }
            }

            return results;
        }
    }


    public List<RpcDecoratedResultItem> selectBestResults(int limitByDomain,
                                                          int limitTotal,
                                                          ResultRankingContext resultRankingContext,
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

        if (resultRankingContext.params.getExportDebugData()) {
            var combinedIdsList = new LongArrayList(resultsList.size());
            for (var item : resultsList) {
                combinedIdsList.add(item.combinedId);
            }

            resultsList.clear();
            IndexSearchBudget budget = new IndexSearchBudget(10000);
            resultsList.addAll(this.rankResults(
                    resultRankingContext,
                    budget, new CombinedDocIdList(combinedIdsList),
                    true)
            );
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

                CqDataLong termIds = resultRankingContext.compiledQueryIds.data;

                for (var entry : debugFactors.getTermFactors()) {
                    String term = "[ERROR IN LOOKUP]";

                    // CURSED: This is a linear search, but the number of terms is small, and it's in a debug path
                    for (int i = 0; i < termIds.size(); i++) {
                        if (termIds.get(i) == entry.termId()) {
                            term = resultRankingContext.compiledQuery.at(i);
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


    public QuerySearchTerms getSearchTerms(CompiledQuery<String> compiledQuery, SearchQuery searchQuery) {

        LongArrayList termIdsList = new LongArrayList();

        TObjectLongHashMap<String> termToId = new TObjectLongHashMap<>(10, 0.75f, -1);

        for (String word : compiledQuery) {
            long id = SearchTermsUtil.getWordId(word);
            termIdsList.add(id);
            termToId.put(word, id);
        }

        for (var term : searchQuery.searchTermsPriority) {
            if (termToId.containsKey(term)) {
                continue;
            }

            long id = SearchTermsUtil.getWordId(term);
            termIdsList.add(id);
            termToId.put(term, id);
        }

        var idsAll = new TermIdList(termIdsList);

        var constraintsMandatory = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsFull = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();
        var constraintsOptional = new ArrayList<PhraseConstraintGroupList.PhraseConstraintGroup>();

        for (var constraint : searchQuery.phraseConstraints) {
            switch (constraint) {
                case SearchPhraseConstraint.Mandatory(List<String> terms) ->
                        constraintsMandatory.add(new PhraseConstraintGroupList.PhraseConstraintGroup(terms, idsAll));
                case SearchPhraseConstraint.Optional(List<String> terms) ->
                        constraintsOptional.add(new PhraseConstraintGroupList.PhraseConstraintGroup(terms, idsAll));
                case SearchPhraseConstraint.Full(List<String> terms) ->
                        constraintsFull.add(new PhraseConstraintGroupList.PhraseConstraintGroup(terms, idsAll));
            }
        }

        if (constraintsFull.isEmpty()) {
            logger.warn("No full constraints in query, adding empty group");
            constraintsFull.add(new PhraseConstraintGroupList.PhraseConstraintGroup(List.of(), idsAll));
        }


        return new QuerySearchTerms(termToId,
                idsAll,
                new PhraseConstraintGroupList(constraintsFull.getFirst(), constraintsMandatory, constraintsOptional)
        );
    }
}
