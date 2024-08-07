package nu.marginalia.index.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TObjectLongHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcRawResultItem;
import nu.marginalia.api.searchquery.RpcResultKeywordScore;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.index.results.model.TermCoherenceGroupList;
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
import java.util.*;

@Singleton
public class IndexResultRankingService {
    private static final Logger logger = LoggerFactory.getLogger(IndexResultRankingService.class);

    private final DocumentDbReader documentDbReader;
    private final StatefulIndex statefulIndex;

    @Inject
    public IndexResultRankingService(DocumentDbReader documentDbReader,
                                     StatefulIndex statefulIndex)
    {
        this.documentDbReader = documentDbReader;
        this.statefulIndex = statefulIndex;
    }

    public List<SearchResultItem> rankResults(SearchParameters params,
                                              ResultRankingContext rankingContext,
                                              CombinedDocIdList resultIds)
    {
        IndexResultScoreCalculator resultRanker = new IndexResultScoreCalculator(statefulIndex, rankingContext, params);

        List<SearchResultItem> results = new ArrayList<>(resultIds.size());

        // Get the current index reader, which is the one we'll use for this calculation,
        // this may change during the calculation, but we don't want to switch over mid-calculation
        final CombinedIndexReader currentIndex = statefulIndex.get();

        final QuerySearchTerms searchTerms = getSearchTerms(params.compiledQuery, params.query);
        final int termCount = searchTerms.termIdsAll.size();

        // We use an arena for the position data to avoid gc pressure
        // from the gamma coded sequences, which can be large and have a lifetime
        // that matches the try block here
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

            // Iterate over documents by their index in the combinedDocIds, as we need the index for the
            // term data arrays as well

            for (int i = 0; i < resultIds.size(); i++) {

                // Prepare term-level data for the document
                for (int ti = 0; ti < flags.length; ti++) {
                    var tfd = termsForDocs[ti];

                    assert tfd != null : "No term data for term " + ti;

                    flags[ti] = tfd.flag(i);
                    positions[ti] = tfd.position(i);
                }

                // Ignore documents that don't match the mandatory constraints
                if (!searchTerms.coherences.testMandatory(positions)) {
                    continue;
                }

                // Calculate the preliminary score
                var score = resultRanker.calculateScore(arena, resultIds.at(i), searchTerms, flags, positions);
                if (score != null) {
                    results.add(score);
                }
            }

            return results;
        }
    }


    public List<RpcDecoratedResultItem> selectBestResults(SearchParameters params,
                                                          Collection<SearchResultItem> results) throws SQLException {

        var domainCountFilter = new IndexResultDomainDeduplicator(params.limitByDomain);

        List<SearchResultItem> resultsList = new ArrayList<>(results.size());
        TLongList idsList = new TLongArrayList(params.limitTotal);

        for (var item : results) {
            if (domainCountFilter.test(item)) {

                if (resultsList.size() < params.limitTotal) {
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

        // Fetch the document details for the selected results in one go, from the local document database
        // for this index partition
        Map<Long, DocdbUrlDetail> detailsById = new HashMap<>(idsList.size());
        for (var item : documentDbReader.getUrlDetails(idsList)) {
            detailsById.put(item.urlId(), item);
        }

        List<RpcDecoratedResultItem> resultItems = new ArrayList<>(resultsList.size());

        // Decorate the results with the document details
        for (var result : resultsList) {
            final long id = result.getDocumentId();
            final DocdbUrlDetail docData = detailsById.get(id);

            if (docData == null) {
                logger.warn("No document data for id {}", id);
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
                    .setBestPositions(0 /* FIXME */)
                    .setResultsFromDomain(domainCountFilter.getCount(result))
                    .setRawItem(rawItem);

            if (docData.pubYear() != null) {
                decoratedBuilder.setPubYear(docData.pubYear());
            }

            /* FIXME
            var rankingDetails = IndexProtobufCodec.convertRankingDetails(result.rankingDetails);
            if (rankingDetails != null) {
                decoratedBuilder.setRankingDetails(rankingDetails);
            }*/
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

        var constraints = new ArrayList<TermCoherenceGroupList.TermCoherenceGroup>();
        for (var coherence : searchQuery.searchTermCoherences) {
            constraints.add(new TermCoherenceGroupList.TermCoherenceGroup(coherence, idsAll));
        }

        return new QuerySearchTerms(termToId,
                idsAll,
                new TermCoherenceGroupList(constraints)
        );
    }
}
