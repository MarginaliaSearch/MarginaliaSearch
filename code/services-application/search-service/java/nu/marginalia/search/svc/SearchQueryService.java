package nu.marginalia.search.svc;

import com.google.inject.Inject;
import io.jooby.ModelAndView;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.QueryParam;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.command.*;
import nu.marginalia.search.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SearchQueryService {

    private final WebsiteUrl websiteUrl;
    private final SearchErrorPageService errorPageService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<SearchCommandInterface> specialCommands = new ArrayList<>();
    private final SearchCommand defaultCommand;

    @Inject
    public SearchQueryService(
            WebsiteUrl websiteUrl,
            SearchErrorPageService errorPageService,
            BrowseRedirectCommand redirectCommand,
            ConvertCommand convertCommand,
            DefinitionCommand definitionCommand,
            BangCommand bangCommand,
            SiteRedirectCommand siteRedirectCommand,
            SearchCommand searchCommand
    ) {
        this.websiteUrl = websiteUrl;
        this.errorPageService = errorPageService;

        specialCommands.add(redirectCommand);
        specialCommands.add(convertCommand);
        specialCommands.add(definitionCommand);
        specialCommands.add(bangCommand);
        specialCommands.add(siteRedirectCommand);

        defaultCommand = searchCommand;
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
            @QueryParam String lang,
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
                    Objects.requireNonNullElse(lang, "en"),
                    false,
                    Objects.requireNonNullElse(page,1));

            for (var cmd : specialCommands) {
                var maybe = cmd.process(parameters);
                if (maybe.isPresent())
                    return maybe.get();
            }

            return defaultCommand.process(parameters).orElseThrow();
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            return errorPageService.serveError(
                    SearchParameters.defaultsForQuery(websiteUrl, query, Objects.requireNonNullElse(page, 1))
            );
        }
    }

}
