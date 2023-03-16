package nu.marginalia.index.results;

import gnu.trove.list.TLongList;
import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.index.client.model.results.SearchResultPreliminaryScore;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.query.IndexQueryParams;

import java.util.List;

public class IndexResultValuator {
    private final IndexMetadataService metadataService;
    private final List<List<String>> searchTermVariants;
    private final IndexQueryParams queryParams;
    private final TLongHashSet resultsWithPriorityTerms;

    private final IndexMetadataService.TermMetadata termMetadata;
    private final IndexMetadataService.QuerySearchTerms searchTerms;

    public IndexResultValuator(IndexMetadataService metadataService,
                               TLongList results,
                               List<SearchSubquery> subqueries,
                               IndexQueryParams queryParams
                               ) {

        final long[] resultsArray = results.toArray();

        this.searchTermVariants = subqueries.stream().map(sq -> sq.searchTermsInclude).distinct().toList();
        this.queryParams = queryParams;
        this.metadataService = metadataService;

        this.searchTerms = metadataService.getSearchTerms(subqueries);
        this.termMetadata = metadataService.getTermMetadata(results.toArray(), searchTerms.termIdsAll);

        resultsWithPriorityTerms = metadataService.getResultsWithPriorityTerms(subqueries, resultsArray);
    }

    private final int flagsFilterMask =
            WordFlags.Title.asBit() | WordFlags.NamesWords.asBit() | WordFlags.Subjects.asBit() | WordFlags.TfIdfHigh.asBit();

    public SearchResultItem calculatePreliminaryScore(long id) {

        SearchResultItem searchResult = new SearchResultItem(id);
        final long urlIdInt = searchResult.getUrlIdInt();

        searchResult.setDomainId(metadataService.getDomainId(urlIdInt));

        long docMetadata = metadataService.getDocumentMetadata(urlIdInt);

        int maxPosCount = 0;
        int maxBitMask = 0;
        int maxFlagsCount = 0;
        boolean hasSingleTermMatch = false;

        for (int querySetId = 0; querySetId < searchTermVariants.size(); querySetId++) {

            var termList = searchTermVariants.get(querySetId);

            SearchResultKeywordScore[] termScoresForSet = new SearchResultKeywordScore[termList.size()];

            for (int termIdx = 0; termIdx < termList.size(); termIdx++) {
                String searchTerm = termList.get(termIdx);

                long metadata = termMetadata.getTermMetadata(
                        searchTerms.get(searchTerm),
                        searchResult.getUrlIdInt()
                );

                var score = new SearchResultKeywordScore(
                        querySetId,
                        searchTerm,
                        metadata,
                        docMetadata,
                        resultsWithPriorityTerms.contains(searchResult.combinedId)
                );

                searchResult.keywordScores.add(score);

                termScoresForSet[termIdx] = score;
            }

            if (!meetsQueryStrategyRequirements(termScoresForSet, queryParams.queryStrategy())) {
                continue;
            }

            int minFlagsCount = 8;
            int minPosCount = 1000;
            int cominedBitMask = ~0;

            for (var termScore : termScoresForSet) {
                final int positionCount = Integer.bitCount(termScore.positions());
                final int flagCount = Long.bitCount(termScore.encodedWordMetadata() & flagsFilterMask);

                minPosCount = Math.min(minPosCount, positionCount);
                minFlagsCount = Math.min(minFlagsCount, flagCount);
                cominedBitMask &= termScore.positions();
            }

            final int combinedBitmaskBitCount = Integer.bitCount(cominedBitMask);

            // Calculate the highest value (overall) of the lowest value (per set) of these search result importance measures
            maxBitMask = Math.max(maxBitMask, combinedBitmaskBitCount);
            maxPosCount = Math.max(maxPosCount, minPosCount);
            maxFlagsCount = Math.max(maxFlagsCount, minFlagsCount);

            hasSingleTermMatch |= (termScoresForSet.length == 1 && minPosCount != 0);
        }

        final boolean hasPriorityTerm = resultsWithPriorityTerms.contains(id);

        searchResult.setScore(new SearchResultPreliminaryScore(
                hasSingleTermMatch,
                hasPriorityTerm,
                maxFlagsCount,
                Math.min(4, maxPosCount),
                Math.min(4, maxBitMask)
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
