package nu.marginalia.api.searchquery;

import nu.marginalia.api.searchquery.model.query.SearchPhraseConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.results.Bm25Parameters;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
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
        List<SearchPhraseConstraint> phraeConstraints = new ArrayList<>();

        for (int j = 0; j < query.getPhrasesCount(); j++) {
            var coh = query.getPhrases(j);
            if (coh.getType() == RpcPhrases.TYPE.OPTIONAL) {
                phraeConstraints.add(new SearchPhraseConstraint.Optional(List.copyOf(coh.getTermsList())));
            }
            else if (coh.getType() == RpcPhrases.TYPE.MANDATORY) {
                phraeConstraints.add(new SearchPhraseConstraint.Mandatory(List.copyOf(coh.getTermsList())));
            }
            else if (coh.getType() == RpcPhrases.TYPE.FULL) {
                phraeConstraints.add(new SearchPhraseConstraint.Full(List.copyOf(coh.getTermsList())));
            }
            else {
                throw new IllegalArgumentException("Unknown phrase constraint type: " + coh.getType());
            }
        }

        return new SearchQuery(
                query.getCompiledQuery(),
                query.getIncludeList(),
                query.getExcludeList(),
                query.getAdviceList(),
                query.getPriorityList(),
                phraeConstraints
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

        for (var constraint : searchQuery.phraseConstraints) {
            switch (constraint) {
                case SearchPhraseConstraint.Optional(List<String> terms) ->
                    subqueryBuilder.addPhrasesBuilder()
                            .addAllTerms(terms)
                            .setType(RpcPhrases.TYPE.OPTIONAL);
                case SearchPhraseConstraint.Mandatory(List<String> terms) ->
                    subqueryBuilder.addPhrasesBuilder()
                            .addAllTerms(terms)
                            .setType(RpcPhrases.TYPE.MANDATORY);
                case SearchPhraseConstraint.Full(List<String> terms) ->
                    subqueryBuilder.addPhrasesBuilder()
                            .addAllTerms(terms)
                            .setType(RpcPhrases.TYPE.FULL);
            }
        }

        return subqueryBuilder.build();
    }

    public static ResultRankingParameters convertRankingParameterss(RpcResultRankingParameters params) {
        if (params == null)
            return ResultRankingParameters.sensibleDefaults();

        return new ResultRankingParameters(
                new Bm25Parameters(params.getBm25K(), params.getBm25B()),
                params.getShortDocumentThreshold(),
                params.getShortDocumentPenalty(),
                params.getDomainRankBonus(),
                params.getQualityPenalty(),
                params.getShortSentenceThreshold(),
                params.getShortSentencePenalty(),
                params.getBm25Weight(),
                params.getTcfFirstPositionWeight(),
                params.getTcfVerbatimWeight(),
                params.getTcfProximityWeight(),
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
                        .setBm25B(rankingParams.bm25Params.b())
                        .setBm25K(rankingParams.bm25Params.k())
                        .setShortDocumentThreshold(rankingParams.shortDocumentThreshold)
                        .setShortDocumentPenalty(rankingParams.shortDocumentPenalty)
                        .setDomainRankBonus(rankingParams.domainRankBonus)
                        .setQualityPenalty(rankingParams.qualityPenalty)
                        .setShortSentenceThreshold(rankingParams.shortSentenceThreshold)
                        .setShortSentencePenalty(rankingParams.shortSentencePenalty)
                        .setBm25Weight(rankingParams.bm25Weight)
                        .setTcfFirstPositionWeight(rankingParams.tcfFirstPosition)
                        .setTcfProximityWeight(rankingParams.tcfProximity)
                        .setTcfVerbatimWeight(rankingParams.tcfVerbatim)
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

}
