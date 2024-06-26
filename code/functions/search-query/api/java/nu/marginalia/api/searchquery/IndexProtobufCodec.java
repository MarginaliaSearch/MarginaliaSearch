package nu.marginalia.api.searchquery;

import nu.marginalia.api.searchquery.model.query.SearchCoherenceConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.results.Bm25Parameters;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingDetails;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingInputs;
import nu.marginalia.api.searchquery.model.results.debug.ResultRankingOutputs;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.index.query.limit.SpecificationLimitType;

import java.util.ArrayList;
import java.util.List;

public class IndexProtobufCodec {

    public static SpecificationLimit convertSpecLimit(RpcSpecLimit limit) {
        return new SpecificationLimit(
                SpecificationLimitType.valueOf(limit.getType().name()),
                limit.getValue()
        );
    }

    public static RpcSpecLimit convertSpecLimit(SpecificationLimit limit) {
        return RpcSpecLimit.newBuilder()
                .setType(RpcSpecLimit.TYPE.valueOf(limit.type().name()))
                .setValue(limit.value())
                .build();
    }

    public static  QueryLimits convertQueryLimits(RpcQueryLimits queryLimits) {
        return new QueryLimits(
                queryLimits.getResultsByDomain(),
                queryLimits.getResultsTotal(),
                queryLimits.getTimeoutMs(),
                queryLimits.getFetchSize()
        );
    }

    public static RpcQueryLimits convertQueryLimits(QueryLimits queryLimits) {
        return RpcQueryLimits.newBuilder()
                .setResultsByDomain(queryLimits.resultsByDomain())
                .setResultsTotal(queryLimits.resultsTotal())
                .setTimeoutMs(queryLimits.timeoutMs())
                .setFetchSize(queryLimits.fetchSize())
                .build();
    }

    public static SearchQuery convertRpcQuery(RpcQuery query) {
        List<SearchCoherenceConstraint>  coherences = new ArrayList<>();

        for (int j = 0; j < query.getCoherencesCount(); j++) {
            var coh = query.getCoherences(j);
            if (coh.getType() == RpcCoherences.TYPE.OPTIONAL) {
                coherences.add(new SearchCoherenceConstraint(false, List.copyOf(coh.getCoherencesList())));
            }
            else if (coh.getType() == RpcCoherences.TYPE.MANDATORY) {
                coherences.add(new SearchCoherenceConstraint(true, List.copyOf(coh.getCoherencesList())));
            }
            else {
                throw new IllegalArgumentException("Unknown coherence type: " + coh.getType());
            }
        }

        return new SearchQuery(
                query.getCompiledQuery(),
                query.getIncludeList(),
                query.getExcludeList(),
                query.getAdviceList(),
                query.getPriorityList(),
                coherences
        );
    }

    public static RpcQuery convertRpcQuery(SearchQuery searchQuery) {
        var subqueryBuilder =
                RpcQuery.newBuilder()
                        .setCompiledQuery(searchQuery.compiledQuery)
                        .addAllInclude(searchQuery.getSearchTermsInclude())
                        .addAllAdvice(searchQuery.getSearchTermsAdvice())
                        .addAllExclude(searchQuery.getSearchTermsExclude())
                        .addAllPriority(searchQuery.getSearchTermsPriority());

        for (var coherences : searchQuery.searchTermCoherences) {
            subqueryBuilder.addCoherencesBuilder()
                    .addAllCoherences(coherences.terms())
                    .setType(coherences.mandatory() ? RpcCoherences.TYPE.MANDATORY : RpcCoherences.TYPE.OPTIONAL)
                    .build();
        }

        return subqueryBuilder.build();
    }

    public static ResultRankingParameters convertRankingParameterss(RpcResultRankingParameters params) {
        if (params == null)
            return ResultRankingParameters.sensibleDefaults();

        return new ResultRankingParameters(
                new Bm25Parameters(params.getFullK(), params.getFullB()),
                new Bm25Parameters(params.getPrioK(), params.getPrioB()),
                params.getShortDocumentThreshold(),
                params.getShortDocumentPenalty(),
                params.getDomainRankBonus(),
                params.getQualityPenalty(),
                params.getShortSentenceThreshold(),
                params.getShortSentencePenalty(),
                params.getBm25FullWeight(),
                params.getBm25NgramWeight(),
                params.getBm25PrioWeight(),
                params.getTcfJaccardWeight(),
                params.getTcfOverlapWeight(),
                ResultRankingParameters.TemporalBias.valueOf(params.getTemporalBias().getBias().name()),
                params.getTemporalBiasWeight(),
                params.getExportDebugData()
        );
    }

    public static RpcResultRankingParameters convertRankingParameterss(ResultRankingParameters rankingParams,
                                                                       RpcTemporalBias temporalBias)
    {
        if (rankingParams == null) {
            rankingParams = ResultRankingParameters.sensibleDefaults();
        }

        var builder = RpcResultRankingParameters.newBuilder()
                        .setFullB(rankingParams.fullParams.b())
                        .setFullK(rankingParams.fullParams.k())
                        .setPrioB(rankingParams.prioParams.b())
                        .setPrioK(rankingParams.prioParams.k())
                        .setShortDocumentThreshold(rankingParams.shortDocumentThreshold)
                        .setShortDocumentPenalty(rankingParams.shortDocumentPenalty)
                        .setDomainRankBonus(rankingParams.domainRankBonus)
                        .setQualityPenalty(rankingParams.qualityPenalty)
                        .setShortSentenceThreshold(rankingParams.shortSentenceThreshold)
                        .setShortSentencePenalty(rankingParams.shortSentencePenalty)
                        .setBm25FullWeight(rankingParams.bm25FullWeight)
                        .setBm25NgramWeight(rankingParams.bm25NgramWeight)
                        .setBm25PrioWeight(rankingParams.bm25PrioWeight)
                        .setTcfOverlapWeight(rankingParams.tcfOverlapWeight)
                        .setTcfJaccardWeight(rankingParams.tcfJaccardWeight)
                        .setTemporalBiasWeight(rankingParams.temporalBiasWeight)
                        .setExportDebugData(rankingParams.exportDebugData);

        if (temporalBias != null && temporalBias.getBias() != RpcTemporalBias.Bias.NONE) {
            builder.setTemporalBias(temporalBias);
        }
        else {
            builder.setTemporalBias(RpcTemporalBias.newBuilder()
                    .setBias(RpcTemporalBias.Bias.valueOf(rankingParams.temporalBias.name())));
        }

        return builder.build();
    }


    public static RpcResultRankingDetails convertRankingDetails(ResultRankingDetails rankingDetails) {
        if (rankingDetails == null) {
            return null;
        }

        return RpcResultRankingDetails.newBuilder()
                .setInputs(convertRankingInputs(rankingDetails.inputs()))
                .setOutput(convertRankingOutput(rankingDetails.outputs()))
                .build();
    }

    private static RpcResultRankingOutputs convertRankingOutput(ResultRankingOutputs outputs) {
        return RpcResultRankingOutputs.newBuilder()
                .setAverageSentenceLengthPenalty(outputs.averageSentenceLengthPenalty())
                .setQualityPenalty(outputs.qualityPenalty())
                .setRankingBonus(outputs.rankingBonus())
                .setTopologyBonus(outputs.topologyBonus())
                .setDocumentLengthPenalty(outputs.documentLengthPenalty())
                .setTemporalBias(outputs.temporalBias())
                .setFlagsPenalty(outputs.flagsPenalty())
                .setOverallPart(outputs.overallPart())
                .setTcfOverlap(outputs.tcfOverlap())
                .setTcfJaccard(outputs.tcfJaccard())
                .setBM25F(outputs.bM25F())
                .setBM25N(outputs.bM25N())
                .setBM25P(outputs.bM25P())
                .build();
    }

    private static RpcResultRankingInputs convertRankingInputs(ResultRankingInputs inputs) {
        return RpcResultRankingInputs.newBuilder()
                .setRank(inputs.rank())
                .setAsl(inputs.asl())
                .setQuality(inputs.quality())
                .setSize(inputs.size())
                .setTopology(inputs.topology())
                .setYear(inputs.year())
                .addAllFlags(inputs.flags())
                .build();
    }
}
