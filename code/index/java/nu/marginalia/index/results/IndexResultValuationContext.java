package nu.marginalia.index.results;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
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
    private final QueryParams queryParams;

    private final TermMetadataForCombinedDocumentIds termMetadataForCombinedDocumentIds;
    private final QuerySearchTerms searchTerms;

    private final ResultRankingContext rankingContext;
    private final ResultValuator searchResultValuator;
    private final CompiledQuery<String> compiledQuery;
    private final CompiledQueryLong compiledQueryIds;

    public IndexResultValuationContext(IndexMetadataService metadataService,
                                       ResultValuator searchResultValuator,
                                       CombinedDocIdList ids,
                                       StatefulIndex statefulIndex,
                                       ResultRankingContext rankingContext,
                                       SearchParameters params
                               ) {
        this.statefulIndex = statefulIndex;
        this.rankingContext = rankingContext;
        this.searchResultValuator = searchResultValuator;

        this.queryParams = params.queryParams;
        this.compiledQuery = params.compiledQuery;
        this.compiledQueryIds = params.compiledQueryIds;

        this.searchTerms = metadataService.getSearchTerms(params.compiledQuery, params.query);

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

        SearchResultItem searchResult = new SearchResultItem(docId);

        SearchResultKeywordScore[] scores = compiledQuery.indices().mapToObj(idx ->
            new SearchResultKeywordScore(
                    compiledQuery.at(idx),
                    compiledQueryIds.at(idx),
                    termMetadataForCombinedDocumentIds.getTermMetadata(
                            compiledQueryIds.at(idx), combinedId
                    ),
                    docMetadata,
                    htmlFeatures)
        )
                .toArray(SearchResultKeywordScore[]::new);

        // DANGER: IndexResultValuatorService assumes that searchResult.keywordScores has this specific order, as it needs
        // to be able to re-construct its own CompiledQuery<SearchResultKeywordScore> for re-ranking the results.  This is
        // a very flimsy assumption.
        searchResult.keywordScores.addAll(List.of(scores));

        CompiledQuery<SearchResultKeywordScore> queryGraphScores = new CompiledQuery<>(compiledQuery.root, scores);

        boolean allSynthetic = !CompiledQueryAggregates.booleanAggregate(queryGraphScores, score -> !score.hasTermFlag(WordFlags.Synthetic));
        int flagsCount = CompiledQueryAggregates.intMaxMinAggregate(queryGraphScores, score -> Long.bitCount(score.encodedWordMetadata() & flagsFilterMask));
        int positionsCount = CompiledQueryAggregates.intMaxMinAggregate(queryGraphScores, SearchResultKeywordScore::positionCount);

        if (!meetsQueryStrategyRequirements(queryGraphScores, queryParams.queryStrategy())) {
            return null;
        }

        if (flagsCount == 0 && !allSynthetic && positionsCount == 0)
            return null;

        double score = searchResultValuator.calculateSearchResultValue(queryGraphScores,
                5000, // use a dummy value here as it's not present in the index
                rankingContext);

        searchResult.setScore(score);

        return searchResult;
    }

    private boolean meetsQueryStrategyRequirements(CompiledQuery<SearchResultKeywordScore> queryGraphScores,
                                                   QueryStrategy queryStrategy)
    {
        if (queryStrategy == QueryStrategy.AUTO ||
                queryStrategy == QueryStrategy.SENTENCE ||
                queryStrategy == QueryStrategy.TOPIC) {
            return true;
        }

        return CompiledQueryAggregates.booleanAggregate(queryGraphScores,
                docs -> meetsQueryStrategyRequirements(docs, queryParams.queryStrategy()));
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
