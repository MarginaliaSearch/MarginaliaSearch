package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.client.Context;
import nu.marginalia.index.client.IndexClient;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.query.model.QueryParams;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.query.svc.QueryFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.Map;

public class QueryBasicInterface {
    private final MustacheRenderer<Object> renderer;
    private final NodeConfigurationWatcher nodeConfigurationWatcher;
    private final IndexClient indexClient;
    private final QueryFactory queryFactory;
    private final Gson gson = GsonFactory.get();

    @Inject
    public QueryBasicInterface(RendererFactory rendererFactory,
                               NodeConfigurationWatcher nodeConfigurationWatcher,
                               IndexClient indexClient,
                               QueryFactory queryFactory
                               ) throws IOException
    {
        this.renderer = rendererFactory.renderer("search");

        this.nodeConfigurationWatcher = nodeConfigurationWatcher;
        this.indexClient = indexClient;
        this.queryFactory = queryFactory;
    }

    public Object handle(Request request, Response response) {
        String queryParam = request.queryParams("q");
        if (queryParam == null) {
            return renderer.render(new Object());
        }

        int count = request.queryParams("count") == null ? 10 : Integer.parseInt(request.queryParams("count"));
        String set = request.queryParams("set") == null ? "" : request.queryParams("set");

        var query = queryFactory.createQuery(new QueryParams(queryParam, new QueryLimits(
                1, count, 250, 8192
        ), set));

        var rsp = indexClient.query(
                Context.fromRequest(request),
                nodeConfigurationWatcher.getQueryNodes(),
                query.specs
        );

        if (request.headers("Accept").contains("application/json")) {
            response.type("application/json");
            return gson.toJson(rsp);
        }
        else {
            return renderer.render(
                    Map.of("query", queryParam,
                            "results", rsp.results)
            );
        }
    }
}
