package nu.marginalia.search.svc;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.command.SearchAdtechParameter;
import nu.marginalia.search.model.SearchProfile;
import nu.marginalia.client.Context;
import nu.marginalia.search.command.CommandEvaluator;
import nu.marginalia.search.command.SearchJsParameter;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.exceptions.RedirectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class SearchQueryService {

    private final WebsiteUrl websiteUrl;
    private final SearchErrorPageService errorPageService;
    private final CommandEvaluator searchCommandEvaulator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public SearchQueryService(
            WebsiteUrl websiteUrl,
            SearchErrorPageService errorPageService,
            CommandEvaluator searchCommandEvaulator) {
        this.websiteUrl = websiteUrl;
        this.errorPageService = errorPageService;
        this.searchCommandEvaulator = searchCommandEvaulator;
    }

    @SneakyThrows
    public Object pathSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);

        try {
            return searchCommandEvaulator.eval(ctx, response, parseParameters(request));
        }
        catch (RedirectException ex) {
            response.redirect(ex.newUrl);
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            errorPageService.serveError(ctx, request, response);
        }

        return "";
    }

    private SearchParameters parseParameters(Request request) {
        try {
            final String queryParam = request.queryParams("query");

            if (null == queryParam || queryParam.isBlank()) {
                throw new RedirectException(websiteUrl.url());
            }

            return new SearchParameters(queryParam.trim(),
                    SearchProfile.getSearchProfile(request.queryParams("profile")),
                    SearchJsParameter.parse(request.queryParams("js")),
                    SearchAdtechParameter.parse(request.queryParams("adtech")));
        }
        catch (Exception ex) {
            // Bots keep sending bad requests, suppress the error otherwise it will
            // fill up the logs.

            throw new RedirectException(websiteUrl.url());
        }
    }
}
