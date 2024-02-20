package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
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
    private final QueryFactory queryFactory;
    private final Gson gson = GsonFactory.get();

    private final QueryGRPCService queryGRPCService;

    @Inject
    public QueryBasicInterface(RendererFactory rendererFactory,
                               QueryFactory queryFactory,
                               QueryGRPCService queryGRPCService
    ) throws IOException
    {
        this.renderer = rendererFactory.renderer("search");

        this.queryFactory = queryFactory;
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

        var query = queryFactory.createQuery(new QueryParams(queryParam, new QueryLimits(
                domainCount, count, 250, 8192
        ), set));

        var rsp = queryGRPCService.executeQueries(QueryProtobufCodec.convertQuery(queryParam, query), count);

        var results = rsp.stream().map(QueryProtobufCodec::convertQueryResult).toList();

        if (request.headers("Accept").contains("application/json")) {
            response.type("application/json");
            return gson.toJson(rsp);
        }
        else {
            return renderer.render(
                    Map.of("query", queryParam,
                            "results", results)
            );
        }
    }
}
