package nu.marginalia.search.svc;

import com.google.inject.Inject;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.command.CommandEvaluator;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.exceptions.RedirectException;
import nu.marginalia.service.server.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class SearchQueryService {

    private final WebsiteUrl websiteUrl;
    private final SearchErrorPageService errorPageService;
    private final CommandEvaluator searchCommandEvaulator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ScrapeStopper scrapeStopper;
    private final RateLimiter rateLimiter = RateLimiter.queryPerMinuteLimiter(60);
    private final MustacheRenderer<Object> waitRenderer;

    @Inject
    public SearchQueryService(
            WebsiteUrl websiteUrl,
            RendererFactory rendererFactory,
            SearchErrorPageService errorPageService,
            CommandEvaluator searchCommandEvaulator,
            ScrapeStopper scrapeStopper) throws IOException {

        this.waitRenderer = rendererFactory.renderer("search/wait-page");
        this.websiteUrl = websiteUrl;
        this.errorPageService = errorPageService;
        this.searchCommandEvaulator = searchCommandEvaulator;
        this.scrapeStopper = scrapeStopper;
    }

    public Object pathSearch(Request request, Response response) {
        String remoteIp = request.headers("X-Forwarded-For");
        String sst = request.queryParamOrDefault("sst", "");
        ScrapeStopper.TokenState tokenState = scrapeStopper.validateToken(sst, remoteIp);

        if (!rateLimiter.isAllowed() && tokenState != ScrapeStopper.TokenState.VALIDATED) {
            if (tokenState == ScrapeStopper.TokenState.INVALID)
                sst = scrapeStopper.getToken("SEARCH", remoteIp, Duration.ofSeconds(3), Duration.ofMinutes(1), 10);

            int waitDuration = (int) scrapeStopper.getRemaining(sst).orElseThrow().toSeconds() + 1;
            Map<String, String> queryParams = new LinkedHashMap<>();
            request.queryParams().forEach(param -> {
                queryParams.put(param, request.queryParams(param));
            });
            queryParams.put("sst", sst);
            StringJoiner redirUrlBuilder = new StringJoiner("&", "?", "");
            queryParams.forEach((k,v) -> {
                redirUrlBuilder.add(k + "=" + v);
            });


            response.header("Cache-Control", "no-store");

            return waitRenderer.render(Map.of(
                    "waitDuration", waitDuration,
                    "redirUrl", redirUrlBuilder.toString()
            ));
        }


        try {
            return searchCommandEvaulator.eval(response, parseParameters(request));
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
