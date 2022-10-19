package nu.marginalia.wmsa.edge.search.svc;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.WebsiteUrl;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.search.command.CommandEvaluator;
import nu.marginalia.wmsa.edge.search.command.SearchJsParameter;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.exceptions.RedirectException;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Optional;

public class EdgeSearchQueryService {

    private WebsiteUrl websiteUrl;
    private final EdgeSearchErrorPageService errorPageService;
    private final CommandEvaluator searchCommandEvaulator;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public EdgeSearchQueryService(
            WebsiteUrl websiteUrl,
            EdgeSearchErrorPageService errorPageService,
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

        final String profileStr = Optional.ofNullable(request.queryParams("profile")).orElse(EdgeSearchProfile.YOLO.name);
        final String humanQuery = queryParam.trim();

        var params = new SearchParameters(
                EdgeSearchProfile.getSearchProfile(profileStr),
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
            errorPageService.serveError(ctx, response);
        }

        return "";
    }

}
