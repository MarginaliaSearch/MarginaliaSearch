package nu.marginalia.index.results;

import nu.marginalia.api.searchquery.model.compiled.*;
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

        this.termMetadataForCombinedDocumentIds = metadataService.getTermMetadataForDocuments(ids,
                searchTerms.termIdsAll);
    }

    private final long flagsFilterMask =
            WordFlags.Title.asBit() | WordFlags.Subjects.asBit() | WordFlags.UrlDomain.asBit() | WordFlags.UrlPath.asBit() | WordFlags.ExternalLink.asBit();

    @Nullable
    public SearchResultItem calculatePreliminaryScore(long combinedId) {

        long docId = UrlIdCodec.removeRank(combinedId);

        if (!searchTerms.coherences.test(termMetadataForCombinedDocumentIds, combinedId))
            return null;

        long docMetadata = statefulIndex.getDocumentMetadata(docId);
        int htmlFeatures = statefulIndex.getHtmlFeatures(docId);

        SearchResultItem searchResult = new SearchResultItem(docId,
                docMetadata,
                htmlFeatures,
                hasPrioTerm(combinedId));

        long[] wordMetas = new long[compiledQuery.size()];
        SearchResultKeywordScore[] scores = new SearchResultKeywordScore[compiledQuery.size()];

        for (int i = 0; i < wordMetas.length; i++) {
            final long termId = compiledQueryIds.at(i);
            final String term = compiledQuery.at(i);

            wordMetas[i] = termMetadataForCombinedDocumentIds.getTermMetadata(termId, combinedId);
            scores[i] = new SearchResultKeywordScore(term, termId, wordMetas[i]);
        }


        // DANGER: IndexResultValuatorService assumes that searchResult.keywordScores has this specific order, as it needs
        // to be able to re-construct its own CompiledQuery<SearchResultKeywordScore> for re-ranking the results.  This is
        // a very flimsy assumption.
        searchResult.keywordScores.addAll(List.of(scores));

        CompiledQueryLong wordMetasQuery = new CompiledQueryLong(compiledQuery.root, new CqDataLong(wordMetas));

        boolean allSynthetic = CompiledQueryAggregates.booleanAggregate(wordMetasQuery, WordFlags.Synthetic::isPresent);
        int flagsCount = CompiledQueryAggregates.intMaxMinAggregate(wordMetasQuery, wordMeta -> Long.bitCount(wordMeta & flagsFilterMask));
        int positionsCount = CompiledQueryAggregates.intMaxMinAggregate(wordMetasQuery, wordMeta -> Long.bitCount(WordMetadata.decodePositions(wordMeta)));

        if (!meetsQueryStrategyRequirements(wordMetasQuery, queryParams.queryStrategy())) {
            return null;
        }

        if (flagsCount == 0 && !allSynthetic && positionsCount == 0)
            return null;

        double score = searchResultValuator.calculateSearchResultValue(
                wordMetasQuery,
                docMetadata,
                htmlFeatures,
                5000, // use a dummy value here as it's not present in the index
                rankingContext,
                null);

        if (searchResult.hasPrioTerm) {
            score = 0.75 * score;
        }

        searchResult.setScore(score);

        return searchResult;
    }

    private boolean hasPrioTerm(long combinedId) {
        for (var term : searchTerms.termIdsPrio.array()) {
            if (termMetadataForCombinedDocumentIds.hasTermMeta(term, combinedId)) {
                return true;
            }
        }
        return  false;
    }

    private boolean meetsQueryStrategyRequirements(CompiledQueryLong queryGraphScores,
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

    private boolean meetsQueryStrategyRequirements(long wordMeta, QueryStrategy queryStrategy) {
        if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SITE) {
            return WordFlags.Site.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_SUBJECT) {
            return WordFlags.Subjects.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_TITLE) {
            return WordFlags.Title.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_URL) {
            return WordFlags.UrlPath.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_DOMAIN) {
            return WordFlags.UrlDomain.isPresent(wordMeta);
        }
        else if (queryStrategy == QueryStrategy.REQUIRE_FIELD_LINK) {
            return WordFlags.ExternalLink.isPresent(wordMeta);
        }
        return true;
    }

}
