package nu.marginalia.index.client;

import nu.marginalia.index.api.*;
import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.client.model.results.Bm25Parameters;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
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

    public static SearchSubquery convertSearchSubquery(RpcSubquery subquery) {
        List<List<String>>  coherences = new ArrayList<>();

        for (int j = 0; j < subquery.getCoherencesCount(); j++) {
            var coh = subquery.getCoherences(j);
            coherences.add(new ArrayList<>(coh.getCoherencesList()));
        }

        return new SearchSubquery(
                subquery.getIncludeList(),
                subquery.getExcludeList(),
                subquery.getAdviceList(),
                subquery.getPriorityList(),
                coherences
        );
    }

    public static RpcSubquery convertSearchSubquery(SearchSubquery searchSubquery) {
        var subqueryBuilder =
                RpcSubquery.newBuilder()
                        .addAllAdvice(searchSubquery.getSearchTermsAdvice())
                        .addAllExclude(searchSubquery.getSearchTermsExclude())
                        .addAllInclude(searchSubquery.getSearchTermsInclude())
                        .addAllPriority(searchSubquery.getSearchTermsPriority());
        for (var coherences : searchSubquery.searchTermCoherences) {
            subqueryBuilder.addCoherencesBuilder().addAllCoherences(coherences);
        }
        return subqueryBuilder.build();
    }

    public static ResultRankingParameters convertRankingParameterss(RpcResultRankingParameters params) {
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
                params.getBm25PrioWeight(),
                params.getTcfWeight(),
                ResultRankingParameters.TemporalBias.valueOf(params.getTemporalBias().name()),
                params.getTemporalBiasWeight()
        );
    };

    public static RpcResultRankingParameters convertRankingParameterss(ResultRankingParameters rankingParams) {
        return
                RpcResultRankingParameters.newBuilder()
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
                        .setBm25PrioWeight(rankingParams.bm25PrioWeight)
                        .setTcfWeight(rankingParams.tcfWeight)
                        .setTemporalBias(RpcResultRankingParameters.TEMPORAL_BIAS.valueOf(rankingParams.temporalBias.name()))
                        .setTemporalBiasWeight(rankingParams.temporalBiasWeight)
                        .build();
    }

}
