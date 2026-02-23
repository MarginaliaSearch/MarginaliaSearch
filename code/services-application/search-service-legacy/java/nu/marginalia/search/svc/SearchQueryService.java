package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.search.ScrapeStopperInterceptor;
import nu.marginalia.search.command.CommandEvaluator;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.exceptions.RedirectException;
import nu.marginalia.service.server.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

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

    public Object pathSearch(Request request, Response response) {

        SearchParameters params = parseParameters(request);

        var intercept = scrapeStopperInterceptor.intercept("S", params.query(), rateLimiter, request, response);
        if (intercept instanceof ScrapeStopperInterceptor.InterceptRedirect redir)
            return redir.result();

        try {
            return searchCommandEvaulator.eval(response,
                    params.withSst(intercept.sst())
            );
        }
        catch (RedirectException ex) {
            response.redirect(ex.newUrl);
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            errorPageService.serveError(request, response);
        }

        return "";
    }

    private SearchParameters parseParameters(Request request) {
        try {
            final String queryParam = request.queryParams("query");

            if (null == queryParam || queryParam.isBlank()) {
                throw new RedirectException(websiteUrl.url());
            }

            return new SearchParameters(queryParam.trim(), request);
        }
        catch (Exception ex) {
            // Bots keep sending bad requests, suppress the error otherwise it will
            // fill up the logs.

            throw new RedirectException(websiteUrl.url());
        }
    }
}
