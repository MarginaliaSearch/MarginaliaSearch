package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
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
    private final MustacheRenderer<Object> renderer;
    private final Gson gson = GsonFactory.get();

    private final QueryGRPCService queryGRPCService;

    @Inject
    public QueryBasicInterface(RendererFactory rendererFactory,
                               QueryGRPCService queryGRPCService
    ) throws IOException
    {
        this.renderer = rendererFactory.renderer("search");
        this.queryGRPCService = queryGRPCService;
    }

    public Object handle(Request request, Response response) {
        String queryParam = request.queryParams("q");
        if (queryParam == null) {
            return renderer.render(new Object());
        }

        int count = request.queryParams("count") == null ? 10 : Integer.parseInt(request.queryParams("count"));
        int domainCount = request.queryParams("domainCount") == null ? 5 : Integer.parseInt(request.queryParams("domainCount"));
        String set = request.queryParams("set") == null ? "" : request.queryParams("set");

        var params = new QueryParams(queryParam, new QueryLimits(
                domainCount, count, 250, 8192
        ), set);

        var results = queryGRPCService.executeDirect(queryParam, params, count);

        if (request.headers("Accept").contains("application/json")) {
            response.type("application/json");
            return gson.toJson(results);
        }
        else {
            return renderer.render(
                    Map.of("query", queryParam,
                            "results", results)
            );
        }
    }
}
