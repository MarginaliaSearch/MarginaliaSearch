package nu.marginalia.query;

import lombok.SneakyThrows;
import nu.marginalia.index.api.*;
import nu.marginalia.index.client.IndexProtobufCodec;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.client.model.results.DecoratedSearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultItem;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.query.model.ProcessedQuery;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.query.model.QueryResponse;

import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.index.client.IndexProtobufCodec.*;

public class QueryProtobufCodec {

    public static RpcIndexQuery convertQuery(RpcQsQuery request, ProcessedQuery query) {
        var builder = RpcIndexQuery.newBuilder();

        builder.addAllDomains(request.getDomainIdsList());

        for (var subquery : query.specs.subqueries) {
            builder.addSubqueries(IndexProtobufCodec.convertSearchSubquery(subquery));
        }

        builder.setSearchSetIdentifier(query.specs.searchSetIdentifier.name());
        builder.setHumanQuery(request.getHumanQuery());

        builder.setQuality(convertSpecLimit(query.specs.quality));
        builder.setYear(convertSpecLimit(query.specs.year));
        builder.setSize(convertSpecLimit(query.specs.size));
        builder.setRank(convertSpecLimit(query.specs.rank));

        builder.setQueryLimits(IndexProtobufCodec.convertQueryLimits(query.specs.queryLimits));
        builder.setQueryStrategy(query.specs.queryStrategy.name());
        builder.setParameters(IndexProtobufCodec.convertRankingParameterss(query.specs.rankingParams));

        return builder.build();
    }

    public static QueryParams convertRequest(RpcQsQuery request) {
        return new QueryParams(
                request.getHumanQuery(),
                request.getNearDomain(),
                request.getTacitIncludesList(),
                request.getTacitExcludesList(),
                request.getTacitPriorityList(),
                request.getTacitAdviceList(),
                convertSpecLimit(request.getQuality()),
                convertSpecLimit(request.getYear()),
                convertSpecLimit(request.getSize()),
                convertSpecLimit(request.getRank()),
                request.getDomainIdsList(),
                IndexProtobufCodec.convertQueryLimits(request.getQueryLimits()),
                SearchSetIdentifier.valueOf(request.getSearchSetIdentifier()));
    }


    public static QueryResponse convertQueryResponse(RpcQsResponse query) {
        var results = new ArrayList<DecoratedSearchResultItem>(query.getResultsCount());

        for (int i = 0; i < query.getResultsCount(); i++)
            results.add(convertDecoratedResult(query.getResults(i)));

        return new QueryResponse(
                convertSearchSpecification(query.getSpecs()),
                results,
                query.getSearchTermsHumanList(),
                query.getProblemsList(),
                query.getDomain()
        );
    }

    @SneakyThrows
    private static DecoratedSearchResultItem convertDecoratedResult(RpcDecoratedResultItem results) {
        return new DecoratedSearchResultItem(
                convertRawResult(results.getRawItem()),
                new EdgeUrl(results.getUrl()),
                results.getTitle(),
                results.getDescription(),
                results.getUrlQuality(),
                results.getFormat(),
                results.getFeatures(),
                results.getPubYear(), // ??,
                results.getDataHash(),
                results.getWordsTotal(),
                results.getRankingScore()
        );
    }

    private static SearchResultItem convertRawResult(RpcRawResultItem rawItem) {
        var keywordScores = new ArrayList<SearchResultKeywordScore>(rawItem.getKeywordScoresCount());

        for (int i = 0; i < rawItem.getKeywordScoresCount(); i++)
            keywordScores.add(convertKeywordScore(rawItem.getKeywordScores(i)));

        return new SearchResultItem(
                rawItem.getCombinedId(),
                keywordScores,
                rawItem.getResultsFromDomain(),
                null
        );
    }

    private static SearchResultKeywordScore convertKeywordScore(RpcResultKeywordScore keywordScores) {
        return new SearchResultKeywordScore(
                keywordScores.getSubquery(),
                keywordScores.getKeyword(),
                keywordScores.getEncodedWordMetadata(),
                keywordScores.getEncodedDocMetadata(),
                keywordScores.getHtmlFeatures(),
                keywordScores.getHasPriorityTerms()
        );
    }

    private static SearchSpecification convertSearchSpecification(RpcIndexQuery specs) {
        List<SearchSubquery> subqueries = new ArrayList<>(specs.getSubqueriesCount());

        for (int i = 0; i < specs.getSubqueriesCount(); i++) {
            subqueries.add(convertSearchSubquery(specs.getSubqueries(i)));
        }

        return new SearchSpecification(
                subqueries,
                specs.getDomainsList(),
                SearchSetIdentifier.valueOf(specs.getSearchSetIdentifier()),
                specs.getHumanQuery(),
                IndexProtobufCodec.convertSpecLimit(specs.getQuality()),
                IndexProtobufCodec.convertSpecLimit(specs.getYear()),
                IndexProtobufCodec.convertSpecLimit(specs.getSize()),
                IndexProtobufCodec.convertSpecLimit(specs.getRank()),
                IndexProtobufCodec.convertQueryLimits(specs.getQueryLimits()),
                QueryStrategy.valueOf(specs.getQueryStrategy()),
                convertRankingParameterss(specs.getParameters())
        );
    }

    public static RpcQsQuery convertQueryParams(QueryParams params) {
        var builder = RpcQsQuery.newBuilder()
                .addAllDomainIds(params.domainIds())
                .addAllTacitAdvice(params.tacitAdvice())
                .addAllTacitExcludes(params.tacitExcludes())
                .addAllTacitIncludes(params.tacitIncludes())
                .addAllTacitPriority(params.tacitPriority())
                .setHumanQuery(params.humanQuery())
                .setQueryLimits(convertQueryLimits(params.limits()))
                .setQuality(convertSpecLimit(params.quality()))
                .setYear(convertSpecLimit(params.year()))
                .setSize(convertSpecLimit(params.size()))
                .setRank(convertSpecLimit(params.rank()))
                .setSearchSetIdentifier(params.identifier().name());

        if (params.nearDomain() != null)
            builder.setNearDomain(params.nearDomain());

        return builder.build();
    }
}
