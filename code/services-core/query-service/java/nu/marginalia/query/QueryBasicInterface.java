package nu.marginalia.query;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.MediaType;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;

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
    public Object handleBasic(Context ctx) {
        String queryString = ctx.query("q").valueOrNull();
        if (queryString == null) {
            return basicRenderer.render(new Object());
        }

        int count = parseInt(requireNonNullElse(ctx.query("count").valueOrNull(), "10"));
        int page = parseInt(requireNonNullElse(ctx.query("page").valueOrNull(), "1"));
        int domainCount = parseInt(requireNonNullElse(ctx.query("domainCount").valueOrNull(), "5"));
        String langIsoCode = requireNonNullElse(ctx.query("lang").valueOrNull(), "en");
        String set = requireNonNullElse(ctx.query("set").valueOrNull(), "");

        IndexClient.Pagination pagination = new IndexClient.Pagination(page, count);

        QueryGRPCService.DetailedDirectResult detailedDirectResult = queryGRPCService.executeDirect(
                queryString,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(domainCount)
                        .setResultsTotal(min(100, count * 10))
                        .setTimeoutMs(250)
                        .build(),
                set,
                langIsoCode,
                pagination,
                PrototypeRankingParameters.sensibleDefaults()
        );

        List<DecoratedSearchResultItem> results = detailedDirectResult.result();

        List<PaginationInfoPage> paginationInfo = new ArrayList<>();

        for (int i = 1; i <= detailedDirectResult.totalResults() / pagination.pageSize(); i++) {
            paginationInfo.add(new PaginationInfoPage(i, i == pagination.page()));
        }

        String accept = ctx.header("Accept").valueOrNull();
        if (accept != null && accept.contains("application/json")) {
            ctx.setResponseType(MediaType.JSON);
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
    public Object handleAdvanced(Context ctx) {
        String queryString = ctx.query("q").valueOrNull();
        if (queryString == null) {
            // Show the default query form if no query is given
            return qdebugRenderer.render(Map.of("rankingParams", PrototypeRankingParameters.sensibleDefaults()));
        }

        int count = parseInt(requireNonNullElse(ctx.query("count").valueOrNull(), "10"));
        int page = parseInt(requireNonNullElse(ctx.query("page").valueOrNull(), "1"));
        int domainCount = parseInt(requireNonNullElse(ctx.query("domainCount").valueOrNull(), "5"));
        String langIsoCode = requireNonNullElse(ctx.query("lang").valueOrNull(), "en");
        String set = requireNonNullElse(ctx.query("set").valueOrNull(), "");

        IndexClient.Pagination pagination = new IndexClient.Pagination(page, count);

        RpcResultRankingParameters rankingParams = debugRankingParamsFromRequest(ctx);

        QueryGRPCService.DetailedDirectResult detailedDirectResult = queryGRPCService.executeDirect(
                queryString,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(domainCount)
                        .setResultsTotal(min(100, count * 10))
                        .setTimeoutMs(250)
                        .build(),
                set,
                langIsoCode,
                pagination,
                rankingParams
        );

        List<DecoratedSearchResultItem> results = detailedDirectResult.result();

        return qdebugRenderer.render(
                Map.of("query", queryString,
                        //"specs", detailedDirectResult.processedQuery().specs, FIXME: What was in this?
                        "rankingParams", rankingParams, // we can't grab this from the specs as it will null the object if it's the default values
                        "results", results)
        );
    }

    private RpcResultRankingParameters debugRankingParamsFromRequest(Context ctx) {
        RpcResultRankingParameters sensibleDefaults = PrototypeRankingParameters.sensibleDefaults();

        RpcTemporalBias.Bias bias = RpcTemporalBias.Bias.valueOf(stringFromRequest(ctx, "temporalBias", "NONE"));

        return RpcResultRankingParameters.newBuilder()
                .setDomainRankBonus(doubleFromRequest(ctx, "domainRankBonus", sensibleDefaults.getDomainRankBonus()))
                .setQualityPenalty(doubleFromRequest(ctx, "qualityPenalty", sensibleDefaults.getQualityPenalty()))
                .setShortDocumentThreshold(intFromRequest(ctx, "shortDocumentThreshold", sensibleDefaults.getShortDocumentThreshold()))
                .setShortDocumentPenalty(doubleFromRequest(ctx, "shortDocumentPenalty", sensibleDefaults.getShortDocumentPenalty()))
                .setTcfFirstPositionWeight(doubleFromRequest(ctx, "tcfFirstPositionWeight", sensibleDefaults.getTcfFirstPositionWeight()))
                .setTcfVerbatimWeight(doubleFromRequest(ctx, "tcfVerbatimWeight", sensibleDefaults.getTcfVerbatimWeight()))
                .setTcfProximityWeight(doubleFromRequest(ctx, "tcfProximityWeight", sensibleDefaults.getTcfProximityWeight()))
                .setBm25B(doubleFromRequest(ctx, "bm25b", sensibleDefaults.getBm25B()))
                .setBm25K(doubleFromRequest(ctx, "bm25k", sensibleDefaults.getBm25K()))
                .setTemporalBias(RpcTemporalBias.newBuilder().setBias(bias).build())
                .setTemporalBiasWeight(doubleFromRequest(ctx, "temporalBiasWeight", sensibleDefaults.getTemporalBiasWeight()))
                .setShortSentenceThreshold(intFromRequest(ctx, "shortSentenceThreshold", sensibleDefaults.getShortSentenceThreshold()))
                .setShortSentencePenalty(doubleFromRequest(ctx, "shortSentencePenalty", sensibleDefaults.getShortSentencePenalty()))
                .setBm25Weight(doubleFromRequest(ctx, "bm25Weight", sensibleDefaults.getBm25Weight()))
                .setDisablePenalties(boolFromRequest(ctx, "disablePenalties", sensibleDefaults.getDisablePenalties()))
                .setExportDebugData(true)
                .build();
    }

    double doubleFromRequest(Context ctx, String param, double defaultValue) {
        String val = ctx.query(param).valueOrNull();
        return Strings.isNullOrEmpty(val) ? defaultValue : Double.parseDouble(val);
    }

    boolean boolFromRequest(Context ctx, String param, boolean defaultValue) {
        if (param == null)
            return defaultValue;

        String val = ctx.query(param).valueOrNull();
        return Strings.isNullOrEmpty(val) ? defaultValue : Boolean.parseBoolean(val);
    }

    int intFromRequest(Context ctx, String param, int defaultValue) {
        String val = ctx.query(param).valueOrNull();
        return Strings.isNullOrEmpty(val) ? defaultValue : parseInt(val);
    }

    String stringFromRequest(Context ctx, String param, String defaultValue) {
        String val = ctx.query(param).valueOrNull();
        return Strings.isNullOrEmpty(val) ? defaultValue : val;
    }

    record PaginationInfoPage(int number, boolean current) {}
}
