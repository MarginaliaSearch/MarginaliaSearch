package nu.marginalia.query;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.api.searchquery.model.results.Bm25Parameters;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.Map;

public class QueryBasicInterface {
    private final MustacheRenderer<Object> basicRenderer;
    private final MustacheRenderer<Object> qdebugRenderer;
    private final Gson gson = GsonFactory.get();

    private final QueryGRPCService queryGRPCService;

    @Inject
    public QueryBasicInterface(RendererFactory rendererFactory,
                               QueryGRPCService queryGRPCService
    ) throws IOException
    {
        this.basicRenderer = rendererFactory.renderer("search");
        this.qdebugRenderer = rendererFactory.renderer("qdebug");
        this.queryGRPCService = queryGRPCService;
    }

    public Object handleBasic(Request request, Response response) {
        String queryParams = request.queryParams("q");
        if (queryParams == null) {
            return basicRenderer.render(new Object());
        }

        int count = request.queryParams("count") == null ? 10 : Integer.parseInt(request.queryParams("count"));
        int domainCount = request.queryParams("domainCount") == null ? 5 : Integer.parseInt(request.queryParams("domainCount"));
        String set = request.queryParams("set") == null ? "" : request.queryParams("set");

        var params = new QueryParams(queryParams, new QueryLimits(
                domainCount, count, 250, 8192
        ), set);

        var detailedDirectResult = queryGRPCService.executeDirect(queryParams,
                params,
                ResultRankingParameters.sensibleDefaults(),
                count);

        var results = detailedDirectResult.result();

        if (request.headers("Accept").contains("application/json")) {
            response.type("application/json");
            return gson.toJson(results);
        }
        else {
            return basicRenderer.render(
                    Map.of("query", queryParams,
                            "results", results)
            );
        }
    }

    public Object handleAdvanced(Request request, Response response) {
        String queryString = request.queryParams("q");
        if (queryString == null) {
            // Show the default query form if no query is given
            return qdebugRenderer.render(Map.of("rankingParams", ResultRankingParameters.sensibleDefaults())
            );
        }

        int count = request.queryParams("count") == null ? 10 : Integer.parseInt(request.queryParams("count"));
        int domainCount = request.queryParams("domainCount") == null ? 5 : Integer.parseInt(request.queryParams("domainCount"));
        String set = request.queryParams("set") == null ? "" : request.queryParams("set");

        var queryParams = new QueryParams(queryString, new QueryLimits(
                domainCount, count, 250, 8192
        ), set);

        var rankingParams = rankingParamsFromRequest(request);

        var detailedDirectResult = queryGRPCService.executeDirect(queryString,
                queryParams,
                rankingParams,
                count);

        var results = detailedDirectResult.result();

        return qdebugRenderer.render(
                Map.of("query", queryString,
                        "specs", detailedDirectResult.processedQuery().specs,
                        "rankingParams", rankingParams, // we can't grab this from the specs as it will null the object if it's the default values
                        "results", results)
        );
    }

    private ResultRankingParameters rankingParamsFromRequest(Request request) {
        var sensibleDefaults = ResultRankingParameters.sensibleDefaults();

        return ResultRankingParameters.builder()
                .domainRankBonus(doubleFromRequest(request, "domainRankBonus", sensibleDefaults.domainRankBonus))
                .qualityPenalty(doubleFromRequest(request, "qualityPenalty", sensibleDefaults.qualityPenalty))
                .shortDocumentThreshold(intFromRequest(request, "shortDocumentThreshold", sensibleDefaults.shortDocumentThreshold))
                .shortDocumentPenalty(doubleFromRequest(request, "shortDocumentPenalty", sensibleDefaults.shortDocumentPenalty))
                .tcfJaccardWeight(doubleFromRequest(request, "tcfJaccardWeight", sensibleDefaults.tcfJaccardWeight))
                .tcfOverlapWeight(doubleFromRequest(request, "tcfOverlapWeight", sensibleDefaults.tcfOverlapWeight))
                .fullParams(new Bm25Parameters(
                        doubleFromRequest(request, "fullParams.k1", sensibleDefaults.fullParams.k()),
                        doubleFromRequest(request, "fullParams.b", sensibleDefaults.fullParams.b())
                ))
                .prioParams(new Bm25Parameters(
                        doubleFromRequest(request, "prioParams.k1", sensibleDefaults.prioParams.k()),
                        doubleFromRequest(request, "prioParams.b", sensibleDefaults.prioParams.b())
                ))
                .temporalBias(ResultRankingParameters.TemporalBias.valueOf(stringFromRequest(request, "temporalBias", sensibleDefaults.temporalBias.toString())))
                .temporalBiasWeight(doubleFromRequest(request, "temporalBiasWeight", sensibleDefaults.temporalBiasWeight))
                .shortSentenceThreshold(intFromRequest(request, "shortSentenceThreshold", sensibleDefaults.shortSentenceThreshold))
                .shortSentencePenalty(doubleFromRequest(request, "shortSentencePenalty", sensibleDefaults.shortSentencePenalty))
                .bm25FullWeight(doubleFromRequest(request, "bm25FullWeight", sensibleDefaults.bm25FullWeight))
                .bm25NgramWeight(doubleFromRequest(request, "bm25NgramWeight", sensibleDefaults.bm25NgramWeight))
                .bm25PrioWeight(doubleFromRequest(request, "bm25PrioWeight", sensibleDefaults.bm25PrioWeight))
                .exportDebugData(true)
                .build();
    }

    double doubleFromRequest(Request request, String param, double defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : Double.parseDouble(request.queryParams(param));
    }

    int intFromRequest(Request request, String param, int defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : Integer.parseInt(request.queryParams(param));
    }

    String stringFromRequest(Request request, String param, String defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : request.queryParams(param);
    }
}
