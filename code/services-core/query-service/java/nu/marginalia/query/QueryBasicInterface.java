package nu.marginalia.query;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.results.Bm25Parameters;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.min;
import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNullElse;

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

    /** Handle the basic search endpoint exposed in the bare-bones search interface. */
    public Object handleBasic(Request request, Response response) {
        String queryString = request.queryParams("q");
        if (queryString == null) {
            return basicRenderer.render(new Object());
        }

        int count = parseInt(requireNonNullElse(request.queryParams("count"), "10"));
        int page = parseInt(requireNonNullElse(request.queryParams("page"), "1"));
        int domainCount = parseInt(requireNonNullElse(request.queryParams("domainCount"), "5"));
        String set = requireNonNullElse(request.queryParams("set"), "");

        var params = new QueryParams(queryString, new QueryLimits(
                domainCount, min(100, count * 10), 250, 8192
        ), set);

        var pagination = new IndexClient.Pagination(page, count);

        var detailedDirectResult = queryGRPCService.executeDirect(
                queryString,
                params,
                pagination,
                ResultRankingParameters.sensibleDefaults()
        );

        var results = detailedDirectResult.result();

        List<PaginationInfoPage> paginationInfo = new ArrayList<>();

        for (int i = 1; i <= detailedDirectResult.totalResults() / pagination.pageSize(); i++) {
            paginationInfo.add(new PaginationInfoPage(i, i == pagination.page()));
        }

        if (request.headers("Accept").contains("application/json")) {
            response.type("application/json");
            return gson.toJson(results);
        }
        else {
            return basicRenderer.render(
                    Map.of("query", queryString,
                            "pages", paginationInfo,
                            "results", results)
            );
        }
    }

    /** Handle the qdebug endpoint, which allows for query debugging and ranking parameter tuning. */
    public Object handleAdvanced(Request request, Response response) {
        String queryString = request.queryParams("q");
        if (queryString == null) {
            // Show the default query form if no query is given
            return qdebugRenderer.render(Map.of("rankingParams", ResultRankingParameters.sensibleDefaults())
            );
        }

        int count = parseInt(requireNonNullElse(request.queryParams("count"), "10"));
        int page = parseInt(requireNonNullElse(request.queryParams("page"), "1"));
        int domainCount = parseInt(requireNonNullElse(request.queryParams("domainCount"), "5"));
        String set = requireNonNullElse(request.queryParams("set"), "");

        var queryParams = new QueryParams(queryString, new QueryLimits(
                domainCount, min(100, count * 10), 250, 8192
        ), set);

        var pagination = new IndexClient.Pagination(page, count);

        var rankingParams = debugRankingParamsFromRequest(request);

        var detailedDirectResult = queryGRPCService.executeDirect(
                queryString,
                queryParams,
                pagination,
                rankingParams
        );

        var results = detailedDirectResult.result();

        return qdebugRenderer.render(
                Map.of("query", queryString,
                        "specs", detailedDirectResult.processedQuery().specs,
                        "rankingParams", rankingParams, // we can't grab this from the specs as it will null the object if it's the default values
                        "results", results)
        );
    }

    private ResultRankingParameters debugRankingParamsFromRequest(Request request) {
        var sensibleDefaults = ResultRankingParameters.sensibleDefaults();

        return ResultRankingParameters.builder()
                .domainRankBonus(doubleFromRequest(request, "domainRankBonus", sensibleDefaults.domainRankBonus))
                .qualityPenalty(doubleFromRequest(request, "qualityPenalty", sensibleDefaults.qualityPenalty))
                .shortDocumentThreshold(intFromRequest(request, "shortDocumentThreshold", sensibleDefaults.shortDocumentThreshold))
                .shortDocumentPenalty(doubleFromRequest(request, "shortDocumentPenalty", sensibleDefaults.shortDocumentPenalty))
                .tcfFirstPosition(doubleFromRequest(request, "tcfFirstPosition", sensibleDefaults.tcfFirstPosition))
                .tcfVerbatim(doubleFromRequest(request, "tcfVerbatim", sensibleDefaults.tcfVerbatim))
                .tcfProximity(doubleFromRequest(request, "tcfProximity", sensibleDefaults.tcfProximity))
                .bm25Params(new Bm25Parameters(
                        doubleFromRequest(request, "bm25.k1", sensibleDefaults.bm25Params.k()),
                        doubleFromRequest(request, "bm25.b", sensibleDefaults.bm25Params.b())
                ))
                .temporalBias(ResultRankingParameters.TemporalBias.valueOf(stringFromRequest(request, "temporalBias", sensibleDefaults.temporalBias.toString())))
                .temporalBiasWeight(doubleFromRequest(request, "temporalBiasWeight", sensibleDefaults.temporalBiasWeight))
                .shortSentenceThreshold(intFromRequest(request, "shortSentenceThreshold", sensibleDefaults.shortSentenceThreshold))
                .shortSentencePenalty(doubleFromRequest(request, "shortSentencePenalty", sensibleDefaults.shortSentencePenalty))
                .bm25Weight(doubleFromRequest(request, "bm25Weight", sensibleDefaults.bm25Weight))
                .exportDebugData(true)
                .build();
    }

    double doubleFromRequest(Request request, String param, double defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : Double.parseDouble(request.queryParams(param));
    }

    int intFromRequest(Request request, String param, int defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : parseInt(request.queryParams(param));
    }

    String stringFromRequest(Request request, String param, String defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : request.queryParams(param);
    }

    record PaginationInfoPage(int number, boolean current) {}
}
