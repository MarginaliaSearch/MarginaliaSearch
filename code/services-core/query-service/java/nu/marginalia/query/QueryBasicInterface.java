package nu.marginalia.query;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.api.IndexClient;
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
        String langIsoCode = requireNonNullElse(request.queryParams("lang"), "en");
        String set = requireNonNullElse(request.queryParams("set"), "");

        var pagination = new IndexClient.Pagination(page, count);

        var detailedDirectResult = queryGRPCService.executeDirect(
                queryString,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(domainCount)
                        .setResultsTotal(min(100, count * 10))
                        .setTimeoutMs(250)
                        .setFetchSize(8192)
                        .build(),
                set,
                langIsoCode,
                pagination,
                PrototypeRankingParameters.sensibleDefaults()
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
            return qdebugRenderer.render(Map.of("rankingParams", PrototypeRankingParameters.sensibleDefaults()));
        }

        int count = parseInt(requireNonNullElse(request.queryParams("count"), "10"));
        int page = parseInt(requireNonNullElse(request.queryParams("page"), "1"));
        int domainCount = parseInt(requireNonNullElse(request.queryParams("domainCount"), "5"));
        String langIsoCode = requireNonNullElse(request.queryParams("lang"), "en");
        String set = requireNonNullElse(request.queryParams("set"), "");

        var pagination = new IndexClient.Pagination(page, count);

        var rankingParams = debugRankingParamsFromRequest(request);

        var detailedDirectResult = queryGRPCService.executeDirect(
                queryString,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(domainCount)
                        .setResultsTotal(min(100, count * 10))
                        .setTimeoutMs(250)
                        .setFetchSize(8192)
                        .build(),
                set,
                langIsoCode,
                pagination,
                rankingParams
        );

        var results = detailedDirectResult.result();

        return qdebugRenderer.render(
                Map.of("query", queryString,
                        //"specs", detailedDirectResult.processedQuery().specs, FIXME: What was in this?
                        "rankingParams", rankingParams, // we can't grab this from the specs as it will null the object if it's the default values
                        "results", results)
        );
    }

    private RpcResultRankingParameters debugRankingParamsFromRequest(Request request) {
        var sensibleDefaults = PrototypeRankingParameters.sensibleDefaults();

        var bias = RpcTemporalBias.Bias.valueOf(stringFromRequest(request, "temporalBias", "NONE"));

        return RpcResultRankingParameters.newBuilder()
                .setDomainRankBonus(doubleFromRequest(request, "domainRankBonus", sensibleDefaults.getDomainRankBonus()))
                .setQualityPenalty(doubleFromRequest(request, "qualityPenalty", sensibleDefaults.getQualityPenalty()))
                .setShortDocumentThreshold(intFromRequest(request, "shortDocumentThreshold", sensibleDefaults.getShortDocumentThreshold()))
                .setShortDocumentPenalty(doubleFromRequest(request, "shortDocumentPenalty", sensibleDefaults.getShortDocumentPenalty()))
                .setTcfFirstPositionWeight(doubleFromRequest(request, "tcfFirstPositionWeight", sensibleDefaults.getTcfFirstPositionWeight()))
                .setTcfVerbatimWeight(doubleFromRequest(request, "tcfVerbatimWeight", sensibleDefaults.getTcfVerbatimWeight()))
                .setTcfProximityWeight(doubleFromRequest(request, "tcfProximityWeight", sensibleDefaults.getTcfProximityWeight()))
                .setBm25B(doubleFromRequest(request, "bm25b", sensibleDefaults.getBm25B()))
                .setBm25K(doubleFromRequest(request, "bm25k", sensibleDefaults.getBm25K()))
                .setTemporalBias(RpcTemporalBias.newBuilder().setBias(bias).build())
                .setTemporalBiasWeight(doubleFromRequest(request, "temporalBiasWeight", sensibleDefaults.getTemporalBiasWeight()))
                .setShortSentenceThreshold(intFromRequest(request, "shortSentenceThreshold", sensibleDefaults.getShortSentenceThreshold()))
                .setShortSentencePenalty(doubleFromRequest(request, "shortSentencePenalty", sensibleDefaults.getShortSentencePenalty()))
                .setBm25Weight(doubleFromRequest(request, "bm25Weight", sensibleDefaults.getBm25Weight()))
                .setDisablePenalties(boolFromRequest(request, "disablePenalties", sensibleDefaults.getDisablePenalties()))
                .setExportDebugData(true)
                .build();
    }

    double doubleFromRequest(Request request, String param, double defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : Double.parseDouble(request.queryParams(param));
    }

    boolean boolFromRequest(Request request, String param, boolean defaultValue) {
        if (param == null)
            return defaultValue;

        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : Boolean.parseBoolean(request.queryParams(param));
    }

    int intFromRequest(Request request, String param, int defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : parseInt(request.queryParams(param));
    }

    String stringFromRequest(Request request, String param, String defaultValue) {
        return Strings.isNullOrEmpty(request.queryParams(param)) ? defaultValue : request.queryParams(param);
    }

    record PaginationInfoPage(int number, boolean current) {}
}
