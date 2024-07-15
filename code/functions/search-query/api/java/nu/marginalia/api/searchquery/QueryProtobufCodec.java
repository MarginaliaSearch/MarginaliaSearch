package nu.marginalia.api.searchquery;

import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingDetails;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingInputs;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingOutputs;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.QueryResponse;

import java.util.ArrayList;

public class QueryProtobufCodec {

    public static RpcIndexQuery convertQuery(RpcQsQuery request, ProcessedQuery query) {
        var builder = RpcIndexQuery.newBuilder();

        builder.addAllDomains(request.getDomainIdsList());

        builder.setQuery(IndexProtobufCodec.convertRpcQuery(query.specs.query));

        builder.setSearchSetIdentifier(query.specs.searchSetIdentifier);
        builder.setHumanQuery(request.getHumanQuery());

        builder.setQuality(IndexProtobufCodec.convertSpecLimit(query.specs.quality));
        builder.setYear(IndexProtobufCodec.convertSpecLimit(query.specs.year));
        builder.setSize(IndexProtobufCodec.convertSpecLimit(query.specs.size));
        builder.setRank(IndexProtobufCodec.convertSpecLimit(query.specs.rank));

        builder.setQueryLimits(IndexProtobufCodec.convertQueryLimits(query.specs.queryLimits));

        // Query strategy may be overridden by the query, but if not, use the one from the request
        if (query.specs.queryStrategy != null && query.specs.queryStrategy != QueryStrategy.AUTO)
            builder.setQueryStrategy(query.specs.queryStrategy.name());
        else
            builder.setQueryStrategy(request.getQueryStrategy());

        if (query.specs.rankingParams != null) {
            builder.setParameters(IndexProtobufCodec.convertRankingParameterss(query.specs.rankingParams, request.getTemporalBias()));
        }

        return builder.build();
    }

    public static RpcIndexQuery convertQuery(String humanQuery, ProcessedQuery query) {
        var builder = RpcIndexQuery.newBuilder();

        builder.setQuery(IndexProtobufCodec.convertRpcQuery(query.specs.query));

        builder.setSearchSetIdentifier(query.specs.searchSetIdentifier);
        builder.setHumanQuery(humanQuery);

        builder.setQuality(IndexProtobufCodec.convertSpecLimit(query.specs.quality));
        builder.setYear(IndexProtobufCodec.convertSpecLimit(query.specs.year));
        builder.setSize(IndexProtobufCodec.convertSpecLimit(query.specs.size));
        builder.setRank(IndexProtobufCodec.convertSpecLimit(query.specs.rank));

        builder.setQueryLimits(IndexProtobufCodec.convertQueryLimits(query.specs.queryLimits));

        // Query strategy may be overridden by the query, but if not, use the one from the request
        builder.setQueryStrategy(query.specs.queryStrategy.name());

        if (query.specs.rankingParams != null) {
            builder.setParameters(IndexProtobufCodec.convertRankingParameterss(
                    query.specs.rankingParams,
                    RpcTemporalBias.newBuilder().setBias(
                                    RpcTemporalBias.Bias.NONE)
                            .build())
            );
        }

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
                IndexProtobufCodec.convertSpecLimit(request.getQuality()),
                IndexProtobufCodec.convertSpecLimit(request.getYear()),
                IndexProtobufCodec.convertSpecLimit(request.getSize()),
                IndexProtobufCodec.convertSpecLimit(request.getRank()),
                request.getDomainIdsList(),
                IndexProtobufCodec.convertQueryLimits(request.getQueryLimits()),
                request.getSearchSetIdentifier(),
                QueryStrategy.valueOf(request.getQueryStrategy()),
                ResultRankingParameters.TemporalBias.valueOf(request.getTemporalBias().getBias().name())
        );
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
                results.getBestPositions(),
                results.getRankingScore(),
                results.getResultsFromDomain(),
                convertRankingDetails(results.getRankingDetails())
        );
    }

    private static ResultRankingDetails convertRankingDetails(RpcResultRankingDetails rankingDetails) {
        if (rankingDetails == null)
            return null;
        var inputs = rankingDetails.getInputs();
        var outputs = rankingDetails.getOutput();

        return new ResultRankingDetails(
                convertRankingInputs(inputs),
                convertRankingOutputs(outputs)
        );

    }

    private static ResultRankingOutputs convertRankingOutputs(RpcResultRankingOutputs outputs) {
        return new ResultRankingOutputs(
                outputs.getAverageSentenceLengthPenalty(),
                outputs.getQualityPenalty(),
                outputs.getRankingBonus(),
                outputs.getTopologyBonus(),
                outputs.getDocumentLengthPenalty(),
                outputs.getTemporalBias(),
                outputs.getFlagsPenalty(),
                outputs.getOverallPart(),
                outputs.getBm25Part(),
                outputs.getTcfAvgDist(),
                outputs.getTcfFirstPosition()

        );
    }

    private static ResultRankingInputs convertRankingInputs(RpcResultRankingInputs inputs) {
        return new ResultRankingInputs(
                inputs.getRank(),
                inputs.getAsl(),
                inputs.getQuality(),
                inputs.getSize(),
                inputs.getTopology(),
                inputs.getYear(),
                inputs.getFlagsList()
        );
    }

    private static SearchResultItem convertRawResult(RpcRawResultItem rawItem) {
        var keywordScores = new ArrayList<SearchResultKeywordScore>(rawItem.getKeywordScoresCount());

        for (int i = 0; i < rawItem.getKeywordScoresCount(); i++)
            keywordScores.add(convertKeywordScore(rawItem.getKeywordScores(i)));

        return new SearchResultItem(
                rawItem.getCombinedId(),
                rawItem.getEncodedDocMetadata(),
                rawItem.getHtmlFeatures(),
                keywordScores,
                rawItem.getHasPriorityTerms(),
                Double.NaN // Not set
        );
    }

    private static SearchResultKeywordScore convertKeywordScore(RpcResultKeywordScore keywordScores) {
        return new SearchResultKeywordScore(
                keywordScores.getKeyword(),
                -1, // termId is internal to index service
                keywordScores.getEncodedWordMetadata()
        );
    }

    private static SearchSpecification convertSearchSpecification(RpcIndexQuery specs) {
        return new SearchSpecification(
                IndexProtobufCodec.convertRpcQuery(specs.getQuery()),
                specs.getDomainsList(),
                specs.getSearchSetIdentifier(),
                specs.getHumanQuery(),
                IndexProtobufCodec.convertSpecLimit(specs.getQuality()),
                IndexProtobufCodec.convertSpecLimit(specs.getYear()),
                IndexProtobufCodec.convertSpecLimit(specs.getSize()),
                IndexProtobufCodec.convertSpecLimit(specs.getRank()),
                IndexProtobufCodec.convertQueryLimits(specs.getQueryLimits()),
                QueryStrategy.valueOf(specs.getQueryStrategy()),
                IndexProtobufCodec.convertRankingParameterss(specs.getParameters())
        );
    }

    public static RpcQsQuery convertQueryParams(QueryParams params) {
        var builder = RpcQsQuery.newBuilder()
                .addAllDomainIds(params.domainIds())
                .addAllTacitAdvice(params.tacitAdvice())
                .addAllTacitExcludes(params.tacitExcludes())
                .addAllTacitPriority(params.tacitPriority())
                .setHumanQuery(params.humanQuery())
                .setQueryLimits(IndexProtobufCodec.convertQueryLimits(params.limits()))
                .setQuality(IndexProtobufCodec.convertSpecLimit(params.quality()))
                .setYear(IndexProtobufCodec.convertSpecLimit(params.year()))
                .setSize(IndexProtobufCodec.convertSpecLimit(params.size()))
                .setRank(IndexProtobufCodec.convertSpecLimit(params.rank()))
                .setSearchSetIdentifier(params.identifier())
                .setQueryStrategy(params.queryStrategy().name())
                .setTemporalBias(RpcTemporalBias.newBuilder()
                        .setBias(RpcTemporalBias.Bias.valueOf(params.temporalBias().name()))
                        .build());

        if (params.nearDomain() != null)
            builder.setNearDomain(params.nearDomain());

        return builder.build();
    }

    @SneakyThrows
    public static DecoratedSearchResultItem convertQueryResult(RpcDecoratedResultItem rpcDecoratedResultItem) {
        return new DecoratedSearchResultItem(
                convertRawResult(rpcDecoratedResultItem.getRawItem()),
                new EdgeUrl(rpcDecoratedResultItem.getUrl()),
                rpcDecoratedResultItem.getTitle(),
                rpcDecoratedResultItem.getDescription(),
                rpcDecoratedResultItem.getUrlQuality(),
                rpcDecoratedResultItem.getFormat(),
                rpcDecoratedResultItem.getFeatures(),
                rpcDecoratedResultItem.getPubYear(),
                rpcDecoratedResultItem.getDataHash(),
                rpcDecoratedResultItem.getWordsTotal(),
                rpcDecoratedResultItem.getBestPositions(),
                rpcDecoratedResultItem.getRankingScore(),
                rpcDecoratedResultItem.getResultsFromDomain(),
                convertRankingDetails(rpcDecoratedResultItem.getRankingDetails())
        );
    }

}
