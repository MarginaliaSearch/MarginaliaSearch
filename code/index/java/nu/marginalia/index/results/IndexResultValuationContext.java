package nu.marginalia.index.results;

import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.index.results.model.TermMetadataForCombinedDocumentIds;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.ranking.results.ResultValuator;

import javax.annotation.Nullable;
import java.util.List;

/** This class is responsible for calculating the score of a search result.
 * It holds the data required to perform the scoring, as there is strong
 * reasons to cache this data, and performs the calculations */
public class IndexResultValuationContext {
    private final StatefulIndex statefulIndex;
    private final List<List<String>> searchTermVariants;
    private final QueryParams queryParams;

    private final TermMetadataForCombinedDocumentIds termMetadataForCombinedDocumentIds;
    private final QuerySearchTerms searchTerms;

    private final ResultRankingContext rankingContext;
    private final ResultValuator searchResultValuator;

    public IndexResultValuationContext(IndexMetadataService metadataService,
                                       ResultValuator searchResultValuator,
                                       CombinedDocIdList ids,
                                       StatefulIndex statefulIndex,
                                       ResultRankingContext rankingContext,
                                       List<SearchSubquery> subqueries,
                                       QueryParams queryParams
                               ) {
        this.statefulIndex = statefulIndex;
        this.rankingContext = rankingContext;
        this.searchResultValuator = searchResultValuator;

        this.searchTermVariants = subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();
        this.queryParams = queryParams;

        this.searchTerms = metadataService.getSearchTerms(subqueries);
        this.termMetadataForCombinedDocumentIds = metadataService.getTermMetadataForDocuments(ids, searchTerms.termIdsAll);
    }

    private final long flagsFilterMask =
            WordFlags.Title.asBit() | WordFlags.Subjects.asBit() | WordFlags.UrlDomain.asBit() | WordFlags.UrlPath.asBit() | WordFlags.ExternalLink.asBit();

    @Nullable
    public SearchResultItem calculatePreliminaryScore(long combinedId) {

        long docId = UrlIdCodec.removeRank(combinedId);

        if (!searchTerms.coherences.test(termMetadataForCombinedDocumentIds, docId))
            return null;

        long docMetadata = statefulIndex.getDocumentMetadata(docId);
        int htmlFeatures = statefulIndex.getHtmlFeatures(docId);

        int maxFlagsCount = 0;
        boolean anyAllSynthetic = false;
        int maxPositionsSet = 0;

        SearchResultItem searchResult = new SearchResultItem(docId,
                searchTermVariants.stream().mapToInt(List::size).sum());

        for (int querySetId = 0;
             querySetId < searchTermVariants.size();
             querySetId++)
        {
            var termList = searchTermVariants.get(querySetId);

            SearchResultKeywordScore[] termScoresForSet = new SearchResultKeywordScore[termList.size()];

            boolean synthetic = true;

            for (int termIdx = 0; termIdx < termList.size(); termIdx++) {
                String searchTerm = termList.get(termIdx);

                long termMetadata = termMetadataForCombinedDocumentIds.getTermMetadata(
                        searchTerms.getIdForTerm(searchTerm),
                        combinedId
                );

                var score = new SearchResultKeywordScore(
                        querySetId,
                        searchTerm,
                        termMetadata,
                        docMetadata,
                        htmlFeatures
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

        searchResult.setScore(score);

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
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_LINK) {
            return WordMetadata.hasFlags(termScore.encodedWordMetadata(), WordFlags.ExternalLink.asBit());
        }
        return true;
    }



}
