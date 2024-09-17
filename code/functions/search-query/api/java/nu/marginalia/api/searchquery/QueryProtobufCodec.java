package nu.marginalia.api.searchquery;

import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.model.query.ProcessedQuery;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.api.searchquery.model.results.debug.DebugFactor;
import nu.marginalia.api.searchquery.model.results.debug.DebugFactorGroup;
import nu.marginalia.api.searchquery.model.results.debug.DebugTermFactorGroup;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingDetails;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.model.EdgeUrl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        var docData = rankingDetails.getDocumentOutputs();
        var termData = rankingDetails.getTermOutputs();

        return new ResultRankingDetails(
                convertDocumentOutputs(docData),
                convertTermData(termData)
        );

    }

    private static List<DebugTermFactorGroup> convertTermData(RpcResultTermRankingOutputs termData) {
        Map<String, Long> termIdByName = new HashMap<>();
        Map<String, List<DebugFactor>> factorsByTerm = new HashMap<>();

        for (int i = 0; i < termData.getTermCount(); i++) {
            termIdByName.put(termData.getTerm(i), termData.getTermId(i));
            factorsByTerm.computeIfAbsent(termData.getTerm(i), k -> new ArrayList<>())
                    .add(new DebugFactor(termData.getFactor(i), termData.getValue(i)));
        }

        Map<String, List<DebugFactorGroup>> factorGroupsByTerm = new HashMap<>();
        for (var entry : factorsByTerm.entrySet()) {
            String term = entry.getKey();
            var factorsList = entry.getValue();

            Map<String, List<DebugFactor>> factorsByGroup = new HashMap<>();

            for (var factor : factorsList) {
                String[] parts = factor.factor().split("\\.");

                String group, name;

                if (parts.length != 2) {
                    group = "unknown";
                    name = parts[0];
                } else {
                    group = parts[0];
                    name = parts[1];
                }


                factorsByGroup.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new DebugFactor(name, factor.value()));
            }

            factorsByGroup.forEach((groupName, groupData) -> {
                factorGroupsByTerm.computeIfAbsent(term, k -> new ArrayList<>())
                        .add(new DebugFactorGroup(groupName, groupData));
            });

        }

        List<DebugTermFactorGroup> groups = new ArrayList<>();

        for (var entry : factorGroupsByTerm.entrySet()) {
            groups.add(new DebugTermFactorGroup(entry.getKey(), termIdByName.get(entry.getKey()), entry.getValue()));
        }

        return groups;
    }

    private static List<DebugFactorGroup> convertDocumentOutputs(RpcResultDocumentRankingOutputs docData) {

        List<DebugFactor> unclusteredFactors = new ArrayList<>();
        for (int i = 0; i < docData.getFactorCount(); i++) {
            String factor = docData.getFactor(i);
            String value = docData.getValue(i);
            unclusteredFactors.add(new DebugFactor(factor, value));
        }

        Map<String, List<DebugFactor>> factorsByGroup = new HashMap<>();

        for (var factor : unclusteredFactors) {
            String factorName = factor.factor();
            String value = factor.value();

            String[] parts = factorName.split("\\.");

            String group, name;

            if (parts.length != 2) {
                group = "unknown";
                name = factorName;
            }
            else {
                group = parts[0];
                name = parts[1];
            }

            factorsByGroup.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(new DebugFactor(name, value));
        }

        List<DebugFactorGroup> groups = new ArrayList<>();
        for (var entry : factorsByGroup.entrySet()) {
            groups.add(new DebugFactorGroup(entry.getKey(), entry.getValue()));
        }

        return groups;
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
                0, // Not set
                null, // Not set
                Double.NaN // Not set
        );
    }

    private static SearchResultKeywordScore convertKeywordScore(RpcResultKeywordScore keywordScores) {
        return new SearchResultKeywordScore(
                keywordScores.getKeyword(),
                -1, // termId is internal to index service
                (byte) keywordScores.getFlags(),
                keywordScores.getPositions()
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
