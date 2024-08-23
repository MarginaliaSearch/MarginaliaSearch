package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.MapModelAndView;
import io.jooby.annotation.GET;
import io.jooby.annotation.HeaderParam;
import io.jooby.annotation.Path;
import io.jooby.annotation.QueryParam;
import lombok.SneakyThrows;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.results.Bm25Parameters;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.model.gson.GsonFactory;

import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

public class QueryBasicInterface {
    private final Gson gson = GsonFactory.get();

    private final QueryGRPCService queryGRPCService;

    @Inject
    @SneakyThrows
    public QueryBasicInterface(QueryGRPCService queryGRPCService
    )
    {
        this.queryGRPCService = queryGRPCService;
    }

    @GET
    @Path("/search")
    public Object handleBasic(
            Context context,
            @HeaderParam("Accept") String acceptHeader,
            @QueryParam("q") String queryParams,
            @QueryParam("count") Integer countParam,
            @QueryParam("domainCount") Integer domainCountParam,
            @QueryParam("set") String setParam
    ) {
        if (queryParams == null) {
            context.setResponseType("text/html");
            return new MapModelAndView("search.hbs", Map.of());
        }

        int count = requireNonNullElse(countParam, 10);
        int domainCount = requireNonNullElse(domainCountParam, 5);
        String set = requireNonNullElse(setParam, "");

        var params = new QueryParams(queryParams, new QueryLimits(
                domainCount, count, 250, 8192
        ), set);

        var detailedDirectResult = queryGRPCService.executeDirect(queryParams,
                params,
                ResultRankingParameters.sensibleDefaults(),
                count);

        var results = detailedDirectResult.result();

        if (acceptHeader.contains("application/json")) {
            context.setResponseType("application/json");
            return gson.toJson(results);
        }
        else {
            context.setResponseType("text/html");
            return new MapModelAndView("search.hbs",
                    Map.of("query", queryParams,
                            "results", results)
            );
        }
    }

    @GET
    @Path("/qdebug")
    public Object handleQdebug(
            Context context,
            @QueryParam("q") String queryString,
            @QueryParam("count") Integer countParam,
            @QueryParam("domainCount") Integer domainCountParam,
            @QueryParam("set") String setParam,
            @QueryParam("domainRankBonus") Double domainRankBonus,
            @QueryParam("qualityPenalty") Double qualityPenalty,
            @QueryParam("shortDocumentThreshold") Integer shortDocumentThreshold,
            @QueryParam("shortDocumentPenalty") Double shortDocumentPenalty,
            @QueryParam("tcfJaccardWeight") Double tcfJaccardWeight,
            @QueryParam("tcfOverlapWeight") Double tcfOverlapWeight,
            @QueryParam("fullParams.k1") Double fullParamsK1,
            @QueryParam("fullParams.b") Double fullParamsB,
            @QueryParam("prioParams.k1") Double prioParamsK1,
            @QueryParam("prioParams.b") Double prioParamsB,
            @QueryParam("temporalBias") String temporalBias,
            @QueryParam("temporalBiasWeight") Double temporalBiasWeight,
            @QueryParam("shortSentenceThreshold") Integer shortSentenceThreshold,
            @QueryParam("shortSentencePenalty") Double shortSentencePenalty,
            @QueryParam("bm25FullWeight") Double bm25FullWeight,
            @QueryParam("bm25NgramWeight") Double bm25NgramWeight,
            @QueryParam("bm25PrioWeight") Double bm25PrioWeight
    ) {
        if (queryString == null) {
            context.setResponseType("text/html");
            return new MapModelAndView("qdebug.hbs",
                    Map.of("rankingParams", ResultRankingParameters.sensibleDefaults())
            );
        }

        int count = requireNonNullElse(countParam, 10);
        int domainCount = requireNonNullElse(domainCountParam, 5);
        String set = requireNonNullElse(setParam, "");

        var queryParams = new QueryParams(queryString, new QueryLimits(
                domainCount, count, 250, 8192
        ), set);

        var sensibleDefaults = ResultRankingParameters.sensibleDefaults();

        var rankingParams = ResultRankingParameters.builder()
                .domainRankBonus(Objects.requireNonNullElse(domainRankBonus, sensibleDefaults.domainRankBonus))
                .qualityPenalty(Objects.requireNonNullElse(qualityPenalty, sensibleDefaults.qualityPenalty))
                .shortDocumentThreshold(Objects.requireNonNullElse(shortDocumentThreshold, sensibleDefaults.shortDocumentThreshold))
                .shortDocumentPenalty(Objects.requireNonNullElse(shortDocumentPenalty, sensibleDefaults.shortDocumentPenalty))
                .tcfJaccardWeight(Objects.requireNonNullElse(tcfJaccardWeight, sensibleDefaults.tcfJaccardWeight))
                .tcfOverlapWeight(Objects.requireNonNullElse(tcfOverlapWeight, sensibleDefaults.tcfOverlapWeight))
                .fullParams(new Bm25Parameters(
                        Objects.requireNonNullElse(fullParamsK1, sensibleDefaults.fullParams.k()),
                        Objects.requireNonNullElse(fullParamsB, sensibleDefaults.fullParams.b())
                ))
                .prioParams(
                        new Bm25Parameters(
                                Objects.requireNonNullElse(prioParamsK1, sensibleDefaults.prioParams.k()),
                                Objects.requireNonNullElse(prioParamsB, sensibleDefaults.prioParams.b())
                        )
                )
                .temporalBias(ResultRankingParameters.TemporalBias.valueOf(Objects.requireNonNullElse(temporalBias, sensibleDefaults.temporalBias.toString())))
                .temporalBiasWeight(Objects.requireNonNullElse(temporalBiasWeight, sensibleDefaults.temporalBiasWeight))
                .shortSentenceThreshold(Objects.requireNonNullElse(shortSentenceThreshold, sensibleDefaults.shortSentenceThreshold))
                .shortSentencePenalty(Objects.requireNonNullElse(shortSentencePenalty, sensibleDefaults.shortSentencePenalty))
                .bm25FullWeight(Objects.requireNonNullElse(bm25FullWeight, sensibleDefaults.bm25FullWeight))
                .bm25NgramWeight(Objects.requireNonNullElse(bm25NgramWeight, sensibleDefaults.bm25NgramWeight))
                .bm25PrioWeight(Objects.requireNonNullElse(bm25PrioWeight, sensibleDefaults.bm25PrioWeight))
                .exportDebugData(true)
                .build();

        var detailedDirectResult = queryGRPCService.executeDirect(queryString,
                queryParams,
                rankingParams,
                count);

        var results = detailedDirectResult.result();

        return new MapModelAndView("qdebug.hbs",
                Map.of(
                        "query", queryString,
                        "specs", detailedDirectResult.processedQuery().specs,
                        "rankingParams", rankingParams,
                        "results", results
                )
        );
    }
}