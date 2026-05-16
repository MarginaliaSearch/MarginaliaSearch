package nu.marginalia.query;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.jooby.Context;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcResultRankingParameters;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;

import java.io.IOException;
import java.util.Map;

import static java.lang.Integer.min;
import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNullElse;

public class QueryDebugInterface {
    private final MustacheRenderer<Object> qdebugRenderer;

    private final QueryGRPCService queryGRPCService;

    @Inject
    public QueryDebugInterface(RendererFactory rendererFactory,
                               QueryGRPCService queryGRPCService
    ) throws IOException
    {
        this.qdebugRenderer = rendererFactory.renderer("qdebug");
        this.queryGRPCService = queryGRPCService;
    }

    /** Handle the qdebug endpoint, which allows for query debugging and ranking parameter tuning. */
    public Object handleAdvanced(Context ctx) {
        String queryString = ctx.query("q").valueOrNull();

        if (queryString == null) {
            // Show the default query form if no query is given
            ctx.setResponseType("text/html");
            return qdebugRenderer.render(Map.of("rankingParams", PrototypeRankingParameters.sensibleDefaults()));
        }

        int count = ctx.query("count").intValue(10);
        int page = ctx.query("page").intValue(1);
        int domainCount = ctx.query("domainCount").intValue(5);
        String langIsoCode = ctx.query("lang").value("en");
        String set = ctx.query("set").value("");

        var pagination = new IndexClient.Pagination(page, count);

        var rankingParams = debugRankingParamsFromRequest(ctx);

        var detailedDirectResult = queryGRPCService.executeDirect(
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

        var results = detailedDirectResult.result();

        ctx.setResponseType("text/html");

        return qdebugRenderer.render(
                Map.of("query", queryString,
                        "rankingParams", rankingParams, // we can't grab this from the specs as it will null the object if it's the default values
                        "results", results)
        );
    }

    private RpcResultRankingParameters debugRankingParamsFromRequest(Context ctx) {
        var sensibleDefaults = PrototypeRankingParameters.sensibleDefaults();

        return RpcResultRankingParameters.newBuilder()
                .setDomainRankBonus(ctx.query("domainRankBonus").doubleValue(sensibleDefaults.getDomainRankBonus()))
                .setQualityPenalty(ctx.query("qualityPenalty").doubleValue(sensibleDefaults.getQualityPenalty()))
                .setShortDocumentThreshold(ctx.query("shortDocumentThreshold").intValue(sensibleDefaults.getShortDocumentThreshold()))
                .setShortDocumentPenalty(ctx.query("shortDocumentPenalty").doubleValue(sensibleDefaults.getShortDocumentPenalty()))
                .setTcfFirstPositionWeight(ctx.query("tcfFirstPositionWeight").doubleValue(sensibleDefaults.getTcfFirstPositionWeight()))
                .setTcfVerbatimWeight(ctx.query("tcfVerbatimWeight").doubleValue(sensibleDefaults.getTcfVerbatimWeight()))
                .setTcfProximityWeight(ctx.query("tcfProximityWeight").doubleValue(sensibleDefaults.getTcfProximityWeight()))
                .setBm25B(ctx.query("bm25b").doubleValue(sensibleDefaults.getBm25B()))
                .setBm25K(ctx.query("bm25k").doubleValue(sensibleDefaults.getBm25K()))
                .setTemporalBias(RpcTemporalBias.newBuilder().setBias(
                        RpcTemporalBias.Bias.valueOf(ctx.query("temporalBias").value("NONE"))
                ).build())
                .setTemporalBiasWeight(ctx.query("temporalBiasWeight").doubleValue(sensibleDefaults.getTemporalBiasWeight()))
                .setShortSentenceThreshold(ctx.query("shortSentenceThreshold").intValue(sensibleDefaults.getShortSentenceThreshold()))
                .setShortSentencePenalty(ctx.query("shortSentencePenalty").doubleValue(sensibleDefaults.getShortSentencePenalty()))
                .setBm25Weight(ctx.query("bm25Weight").doubleValue(sensibleDefaults.getBm25Weight()))
                .setDisablePenalties(ctx.query("disablePenalties").booleanValue())
                .setExportDebugData(true)
                .build();
    }

}
