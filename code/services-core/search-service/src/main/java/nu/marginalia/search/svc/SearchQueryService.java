package nu.marginalia.search.svc;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.WebsiteUrl;
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

import java.util.Optional;

public class SearchQueryService {

    private WebsiteUrl websiteUrl;
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

        final String queryParam = request.queryParams("query");
        if (null == queryParam || queryParam.isBlank()) {
            response.redirect(websiteUrl.url());
            return null;
        }

        final String profileStr = Optional.ofNullable(request.queryParams("profile")).orElse(SearchProfile.YOLO.name);
        final String humanQuery = queryParam.trim();

        var params = new SearchParameters(
                SearchProfile.getSearchProfile(profileStr),
                SearchJsParameter.parse(request.queryParams("js")),
                Boolean.parseBoolean(request.queryParams("detailed"))
        );

        try {
            return searchCommandEvaulator.eval(ctx, params, humanQuery);
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

}
