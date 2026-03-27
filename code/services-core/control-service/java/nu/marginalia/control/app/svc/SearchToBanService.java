package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class SearchToBanService {
    private final ControlBlacklistService blacklistService;
    private final ControlRendererFactory rendererFactory;
    private final QueryClient queryClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ControlRendererFactory.Renderer searchToBanRenderer;

    @Inject
    public SearchToBanService(ControlBlacklistService blacklistService,
                              ControlRendererFactory rendererFactory,
                              QueryClient queryClient)
    {

        this.blacklistService = blacklistService;
        this.rendererFactory = rendererFactory;
        this.queryClient = queryClient;
    }

    public void register(Jooby jooby) throws IOException {
        searchToBanRenderer = rendererFactory.renderer("control/app/search-to-ban");

        jooby.get("/search-to-ban", this::handle);
        jooby.post("/search-to-ban", this::handlePost);
    }

    private Object handle(Context ctx) throws TimeoutException {
        String q = ctx.query("q").valueOrNull();

        Object model;
        if (q == null || q.isBlank()) {
            model = Map.of();
        } else {
            model = executeQuery(q);
        }

        ctx.setResponseType(MediaType.html);
        return searchToBanRenderer.render(model);
    }

    private Object handlePost(Context ctx) throws TimeoutException {
        String query = ctx.form("query").valueOrNull();

        for (String param : ctx.formMap().keySet()) {
            logger.info(param + ": " + ctx.form(param).valueOrNull());
            if ("query".equals(param)) {
                continue;
            }
            EdgeUrl.parse(param).ifPresent(url ->
                    blacklistService.addToBlacklist(url.domain, query)
            );
        }

        Object model;
        if (query == null || query.isBlank()) {
            model = Map.of();
        } else {
            model = executeQuery(query);
        }

        ctx.setResponseType(MediaType.html);
        return searchToBanRenderer.render(model);
    }

    private Object executeQuery(String query) throws TimeoutException {
        return queryClient.search(
                new QueryFilterSpec.NoFilter(),
                query,
                "en",
                NsfwFilterTier.OFF,
                RpcQueryLimits.newBuilder()
                        .setResultsTotal(100)
                        .setResultsByDomain(2)
                        .setTimeoutMs(200)
                        .build(),
                1);
    }
}
