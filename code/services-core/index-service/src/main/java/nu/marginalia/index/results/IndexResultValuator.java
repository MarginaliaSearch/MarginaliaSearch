package nu.marginalia.index.results;

import gnu.trove.list.TLongList;
import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.index.client.model.results.SearchResultPreliminaryScore;
import nu.marginalia.index.client.model.results.ResultRankingContext;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.query.IndexQueryParams;
import nu.marginalia.ranking.ResultValuator;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class IndexResultValuator {
    private final IndexMetadataService metadataService;
    private final List<List<String>> searchTermVariants;
    private final IndexQueryParams queryParams;
    private final TLongHashSet resultsWithPriorityTerms;

    private final IndexMetadataService.TermMetadataForDocuments termMetadataForDocuments;
    private final IndexMetadataService.QuerySearchTerms searchTerms;

    private final ResultRankingContext rankingContext;
    private final ResultValuator searchResultValuator;

    public IndexResultValuator(IndexMetadataService metadataService,
                               TLongList results,
                               ResultRankingContext rankingContext,
                               List<SearchSubquery> subqueries,
                               IndexQueryParams queryParams
                               ) {
        this.rankingContext = rankingContext;
        this.searchResultValuator = metadataService.getSearchResultValuator();

        final long[] ids = results.toArray();
        Arrays.sort(ids);

        this.searchTermVariants = subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();
        this.queryParams = queryParams;
        this.metadataService = metadataService;

        this.searchTerms = metadataService.getSearchTerms(subqueries);
        this.termMetadataForDocuments = metadataService.getTermMetadataForDocuments(ids, searchTerms.termIdsAll);

        resultsWithPriorityTerms = metadataService.getResultsWithPriorityTerms(subqueries, ids);
    }

    private final long flagsFilterMask =
            WordFlags.Title.asBit() | WordFlags.Subjects.asBit() | WordFlags.UrlDomain.asBit() | WordFlags.UrlPath.asBit();

    @Nullable
    public SearchResultItem calculatePreliminaryScore(long id) {

        final long docId = UrlIdCodec.removeRank(id);

        if (!termMetadataForDocuments.testCoherence(docId, searchTerms.coherences))
            return null;

        long docMetadata = metadataService.getDocumentMetadata(docId);
        int htmlFeatures = metadataService.getHtmlFeatures(docId);

        int maxFlagsCount = 0;
        boolean anyAllSynthetic = false;
        int maxPositionsSet = 0;

        SearchResultItem searchResult = new SearchResultItem(id);

        for (int querySetId = 0;
             querySetId < searchTermVariants.size();
             querySetId++)
        {
            var termList = searchTermVariants.get(querySetId);

            SearchResultKeywordScore[] termScoresForSet = new SearchResultKeywordScore[termList.size()];

            boolean synthetic = true;

            for (int termIdx = 0; termIdx < termList.size(); termIdx++) {
                String searchTerm = termList.get(termIdx);

                long termMetadata = termMetadataForDocuments.getTermMetadata(
                        searchTerms.getIdForTerm(searchTerm),
                        searchResult.combinedId
                );

                var score = new SearchResultKeywordScore(
                        querySetId,
                        searchTerm,
                        termMetadata,
                        docMetadata,
                        htmlFeatures,
                        resultsWithPriorityTerms.contains(searchResult.combinedId)
                );

                synthetic &= WordFlags.Synthetic.isPresent(termMetadata);

                searchResult.keywordScores.add(score);

                termScoresForSet[termIdx] = score;
            }

            if (!meetsQueryStrategyRequirements(termScoresForSet, queryParams.queryStrategy())) {
                continue;
            }

            int minFlagsCount = 8;
            int minPositionsSet = 4;

            for (var termScore : termScoresForSet) {
                final int flagCount = Long.bitCount(termScore.encodedWordMetadata() & flagsFilterMask);
                minFlagsCount = Math.min(minFlagsCount, flagCount);
                minPositionsSet = Math.min(minPositionsSet, termScore.positionCount());
            }

            maxFlagsCount = Math.max(maxFlagsCount, minFlagsCount);
            maxPositionsSet = Math.max(maxPositionsSet, minPositionsSet);
            anyAllSynthetic |= synthetic;
        }

        if (maxFlagsCount == 0 && !anyAllSynthetic && maxPositionsSet == 0)
            return null;

        double score = searchResultValuator.calculateSearchResultValue(searchResult.keywordScores,
                5000, // use a dummy value here as it's not present in the index
                rankingContext);

        searchResult.setScore(new SearchResultPreliminaryScore(
                resultsWithPriorityTerms.contains(id),
                score
        ));

        return searchResult;
    }

    private boolean meetsQueryStrategyRequirements(SearchResultKeywordScore[] termSet, QueryStrategy queryStrategy) {
        if (queryStrategy == QueryStrategy.AUTO ||
                queryStrategy == QueryStrategy.SENTENCE ||
                queryStrategy == QueryStrategy.TOPIC) {
            return true;
        }

        for (var keyword : termSet) {
            if (!meetsQueryStrategyRequirements(keyword, queryParams.queryStrategy())) {
                return false;
            }
        }

        return true;
    }

    private boolean meetsQueryStrategyRequirements(SearchResultKeywordScore termScore, QueryStrategy queryStrategy) {
        if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SITE) {
            return WordMetadata.hasFlags(termScore.encodedWordMetadata(), WordFlags.Site.asBit());
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SUBJECT) {
            return WordMetadata.hasFlags(termScore.encodedWordMetadata(), WordFlags.Subjects.asBit());
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_TITLE) {
            return WordMetadata.hasFlags(termScore.encodedWordMetadata(), WordFlags.Title.asBit());
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_URL) {
            return WordMetadata.hasFlags(termScore.encodedWordMetadata(), WordFlags.UrlPath.asBit());
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_DOMAIN) {
            return WordMetadata.hasFlags(termScore.encodedWordMetadata(), WordFlags.UrlDomain.asBit());
        }

        return true;
    }



}
