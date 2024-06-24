package nu.marginalia.index.results;

import nu.marginalia.api.searchquery.model.compiled.*;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.model.QueryParams;
import nu.marginalia.index.results.model.QuerySearchTerms;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.ranking.results.ResultValuator;
import nu.marginalia.sequence.GammaCodedSequence;

import javax.annotation.Nullable;

import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.*;

/** This class is responsible for calculating the score of a search result.
 * It holds the data required to perform the scoring, as there is strong
 * reasons to cache this data, and performs the calculations */
public class IndexResultValuationContext {
    private final CombinedIndexReader index;
    private final QueryParams queryParams;

    private final ResultRankingContext rankingContext;
    private final ResultValuator searchResultValuator;
    private final CompiledQuery<String> compiledQuery;

    public IndexResultValuationContext(ResultValuator searchResultValuator,
                                       StatefulIndex statefulIndex,
                                       ResultRankingContext rankingContext,
                                       SearchParameters params)
    {
        this.index = statefulIndex.get();
        this.rankingContext = rankingContext;
        this.searchResultValuator = searchResultValuator;

        this.queryParams = params.queryParams;
        this.compiledQuery = params.compiledQuery;
    }

    private final long flagsFilterMask = WordFlags.Title.asBit() | WordFlags.Subjects.asBit() | WordFlags.UrlDomain.asBit() | WordFlags.UrlPath.asBit() | WordFlags.ExternalLink.asBit();

    @Nullable
    public SearchResultItem calculatePreliminaryScore(long combinedId,
                                                      QuerySearchTerms searchTerms,
                                                      long[] wordFlags,
                                                      GammaCodedSequence[] positions)
    {
        if (!searchTerms.coherences.testMandatory(positions))
            return null;

        CompiledQuery<GammaCodedSequence> positionsQuery = compiledQuery.root.newQuery(positions);
        CompiledQueryLong wordFlagsQuery = compiledQuery.root.newQuery(wordFlags);
        int[] counts = new int[compiledQuery.size()];
        for (int i = 0; i < counts.length; i++) {
            if (positions[i] != null) {
                counts[i] = positions[i].valueCount();
            }
        }
        CompiledQueryInt positionsCountQuery = compiledQuery.root.newQuery(counts);

        // If the document is not relevant to the query, abort early to reduce allocations and
        // avoid unnecessary calculations
        if (testRelevance(wordFlagsQuery, positionsCountQuery)) {
            return null;
        }

        long docId = UrlIdCodec.removeRank(combinedId);
        long docMetadata = index.getDocumentMetadata(docId);
        int htmlFeatures = index.getHtmlFeatures(docId);
        int docSize = index.getDocumentSize(docId);

        double score = searchResultValuator.calculateSearchResultValue(
                wordFlagsQuery,
                positionsCountQuery,
                positionsQuery,
                docMetadata,
                htmlFeatures,
                docSize,
                rankingContext,
                null);

        SearchResultItem searchResult = new SearchResultItem(docId,
                docMetadata,
                htmlFeatures);

        if (hasPrioTerm(searchTerms, positions)) {
            score = 0.75 * score;
        }

        searchResult.setScore(score);

        return searchResult;
    }

    private boolean testRelevance(CompiledQueryLong wordFlagsQuery, CompiledQueryInt countsQuery) {
        boolean allSynthetic = booleanAggregate(wordFlagsQuery, WordFlags.Synthetic::isPresent);
        int flagsCount = intMaxMinAggregate(wordFlagsQuery, flags ->  Long.bitCount(flags & flagsFilterMask));
        int positionsCount = intMaxMinAggregate(countsQuery, p -> p);

        if (!meetsQueryStrategyRequirements(wordFlagsQuery, queryParams.queryStrategy())) {
            return true;
        }
        if (flagsCount == 0 && !allSynthetic && positionsCount == 0) {
            return true;
        }

        return false;
    }

    private boolean hasPrioTerm(QuerySearchTerms searchTerms, GammaCodedSequence[] positions) {
        var allTerms = searchTerms.termIdsAll;
        var prioTerms = searchTerms.termIdsPrio;

        for (int i = 0; i < allTerms.size(); i++) {
            if (positions[i] != null && prioTerms.contains(allTerms.at(i))) {
                return true;
            }
        }

        return false;
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
