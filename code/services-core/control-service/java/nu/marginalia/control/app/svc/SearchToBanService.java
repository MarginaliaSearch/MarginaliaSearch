package nu.marginalia.control.app.svc;

import com.google.inject.Inject;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

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

    public void register(Jooby jooby) throws IOException {
        var searchToBanRenderer = rendererFactory.renderer("control/app/search-to-ban");

        jooby.get("/search-to-ban", ctx -> searchToBanRenderer.render(handle(ctx)));
        jooby.post("/search-to-ban", ctx -> searchToBanRenderer.render(handle(ctx)));
    }

    public Object handle(Context ctx) throws TimeoutException {
        if (Objects.equals(ctx.getMethod(), "POST")) {
            executeBlacklisting(ctx);

            return findResults(ctx.lookup("query", QUERY, FORM).valueOrNull());
        }

        return findResults(ctx.lookup("q", QUERY, FORM).valueOrNull());
    }

    private Object findResults(String q) throws TimeoutException {
        if (q == null || q.isBlank()) {
            return Map.of();
        } else {
            return executeQuery(q);
        }
    }

    private void executeBlacklisting(Context ctx) {
        String query = ctx.lookup("query", QUERY, FORM).valueOrNull();
        var parameterNames = Stream.concat(ctx.query().toMultimap().keySet().stream(), ctx.form().toMultimap().keySet().stream())
                .distinct().toList();
        for (var param : parameterNames) {
            logger.info(param + ": " + ctx.lookup(param, QUERY, FORM).valueOrNull());
            if ("query".equals(param)) {
                continue;
            }
            EdgeUrl.parse(param).ifPresent(url ->
                    blacklistService.addToBlacklist(url.domain, query)
            );
        }
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
