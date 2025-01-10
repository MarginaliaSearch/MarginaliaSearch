package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.ModelAndView;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.QueryParam;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.command.*;
import nu.marginalia.search.model.SearchProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

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

    @GET
    @Path("/search")
    public ModelAndView<?> pathSearch(
            @QueryParam String query,
            @QueryParam String profile,
            @QueryParam String js,
            @QueryParam String recent,
            @QueryParam String searchTitle,
            @QueryParam String adtech,
            @QueryParam Integer page
    ) {
        try {
            SearchParameters parameters = new SearchParameters(websiteUrl,
                    query,
                    SearchProfile.getSearchProfile(profile),
                    SearchJsParameter.parse(js),
                    SearchRecentParameter.parse(recent),
                    SearchTitleParameter.parse(searchTitle),
                    SearchAdtechParameter.parse(adtech),
                    false,
                    Objects.requireNonNullElse(page,1));

            return searchCommandEvaulator.eval(parameters);
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            return errorPageService.serveError(SearchParameters.defaultsForQuery(websiteUrl, query, page));
        }
    }

}
