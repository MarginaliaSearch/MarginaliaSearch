package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.MediaType;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.ScrapeStopperInterceptor;
import nu.marginalia.search.command.CommandEvaluator;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.exceptions.RedirectException;
import nu.marginalia.service.server.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SearchQueryService {

    private final WebsiteUrl websiteUrl;
    private final SearchErrorPageService errorPageService;
    private final CommandEvaluator searchCommandEvaulator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ScrapeStopperInterceptor scrapeStopperInterceptor;
    private final RateLimiter rateLimiter = RateLimiter.queryPerMinuteLimiter(60);

    @Inject
    public SearchQueryService(
            WebsiteUrl websiteUrl,
            RendererFactory rendererFactory,
            SearchErrorPageService errorPageService,
            CommandEvaluator searchCommandEvaulator,
            ScrapeStopperInterceptor scrapeStopperInterceptor) throws IOException {

        this.websiteUrl = websiteUrl;
        this.errorPageService = errorPageService;
        this.searchCommandEvaulator = searchCommandEvaulator;
        this.scrapeStopperInterceptor = scrapeStopperInterceptor;
    }

    public Object pathSearch(Context ctx) {
        ctx.setResponseType(MediaType.html);

        try {
            SearchParameters params = parseParameters(ctx);

            ScrapeStopperInterceptor.InterceptionResult intercept = scrapeStopperInterceptor.intercept("S", params.query(), rateLimiter, ctx);
            if (intercept instanceof ScrapeStopperInterceptor.InterceptRedirect redir)
                return redir.result();

            return searchCommandEvaulator.eval(
                    params.withSst(intercept.sst()),
                    ctx
            );
        }
        catch (RedirectException ex) {
            ctx.sendRedirect(ex.newUrl);
            return ctx;
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            return errorPageService.serveError(ctx);
        }
    }

    private SearchParameters parseParameters(Context ctx) {
        try {
            final String queryParam = ctx.query("query").valueOrNull();

            if (null == queryParam || queryParam.isBlank()) {
                throw new RedirectException(websiteUrl.url());
            }

            return new SearchParameters(queryParam.trim(), ctx);
        }
        catch (RedirectException ex) {
            throw ex;
        }
        catch (Exception ex) {
            // Bots keep sending bad requests, suppress the error otherwise it will
            // fill up the logs.

            throw new RedirectException(websiteUrl.url());
        }
    }
}
