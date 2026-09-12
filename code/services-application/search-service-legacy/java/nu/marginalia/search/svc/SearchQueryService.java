package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.ScrapeStopperInterceptor;
import nu.marginalia.search.command.CommandEvaluator;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.exceptions.RedirectException;
import nu.marginalia.service.server.RateLimiter;
import io.jooby.Context;


public class SearchQueryService {

    private final WebsiteUrl websiteUrl;
    private final CommandEvaluator searchCommandEvaulator;

    private final ScrapeStopperInterceptor scrapeStopperInterceptor;
    private final RateLimiter rateLimiter = RateLimiter.queryPerMinuteLimiter(60);

    @Inject
    public SearchQueryService(
            WebsiteUrl websiteUrl,
            CommandEvaluator searchCommandEvaulator,
            ScrapeStopperInterceptor scrapeStopperInterceptor) {

        this.websiteUrl = websiteUrl;
        this.searchCommandEvaulator = searchCommandEvaulator;
        this.scrapeStopperInterceptor = scrapeStopperInterceptor;
    }

    public Object pathSearch(Context ctx) {

        SearchParameters params = parseParameters(ctx);

        var intercept = scrapeStopperInterceptor.intercept("S", params.query(), rateLimiter, ctx);
        if (intercept instanceof ScrapeStopperInterceptor.InterceptRedirect redir)
            return redir.result();

        return searchCommandEvaulator.eval(params.withSst(intercept.sst()));
    }

    private SearchParameters parseParameters(Context ctx) {
        try {
            final String queryParam = ctx.query("query").valueOrNull();

            if (null == queryParam || queryParam.isBlank()) {
                throw new RedirectException(websiteUrl.url());
            }

            return new SearchParameters(queryParam.trim(), ctx);
        }
        catch (Exception ex) {
            // Bots keep sending bad requests, suppress the error otherwise it will
            // fill up the logs.

            throw new RedirectException(websiteUrl.url());
        }
    }
}
