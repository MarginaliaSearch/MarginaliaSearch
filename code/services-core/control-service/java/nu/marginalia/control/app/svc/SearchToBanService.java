package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class SearchToBanService {
    private final ControlBlacklistService blacklistService;
    private final ControlRendererFactory rendererFactory;
    private final QueryClient queryClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchToBanService(ControlBlacklistService blacklistService,
                              ControlRendererFactory rendererFactory,
                              QueryClient queryClient)
    {

        this.blacklistService = blacklistService;
        this.rendererFactory = rendererFactory;
        this.queryClient = queryClient;
    }

    public void register() throws IOException {
        var searchToBanRenderer = rendererFactory.renderer("control/app/search-to-ban");

        Spark.get("/search-to-ban", this::handle, searchToBanRenderer::render);
        Spark.post("/search-to-ban", this::handle, searchToBanRenderer::render);
    }

    public Object handle(Request request, Response response) {
        if (Objects.equals(request.requestMethod(), "POST")) {
            executeBlacklisting(request);

            return findResults(request.queryParams("query"));
        }

        return findResults(request.queryParams("q"));
    }

    private Object findResults(String q) {
        if (q == null || q.isBlank()) {
            return Map.of();
        } else {
            return executeQuery(q);
        }
    }

    private void executeBlacklisting(Request request) {
        String query = request.queryParams("query");
        for (var param : request.queryParams()) {
            logger.info(param + ": " + request.queryParams(param));
            if ("query".equals(param)) {
                continue;
            }
            EdgeUrl.parse(param).ifPresent(url ->
                    blacklistService.addToBlacklist(url.domain, query)
            );
        }
    }

    private Object executeQuery(String query) {
        return queryClient.search(new QueryParams(
                query,
                RpcQueryLimits.newBuilder()
                        .setResultsTotal(100)
                        .setResultsByDomain(2)
                        .setTimeoutMs(200)
                        .setFetchSize(8192)
                        .build()
                ,
                "NONE"
        ));
    }
}
