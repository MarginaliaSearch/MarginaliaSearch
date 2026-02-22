package nu.marginalia.search.svc;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.ModelAndView;
import io.jooby.annotation.*;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterParser;
import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec;
import nu.marginalia.scrapestopper.ScrapeStopper;
import nu.marginalia.search.command.*;
import nu.marginalia.search.model.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SearchQueryService {

    private final DbDomainQueries domainQueries;
    private final WebsiteUrl websiteUrl;
    private final SearchErrorPageService errorPageService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<SearchCommandInterface> specialCommands = new ArrayList<>();
    private final SearchCommand defaultCommand;

    private static final SearchFilterParser parser = new SearchFilterParser();
    private static final Cache<String, CompiledSearchFilterSpec> filterSpecCache = CacheBuilder.newBuilder()
            .maximumSize(5000)
            .expireAfterAccess(Duration.ofHours(24))
            .build();

    @Inject
    public SearchQueryService(
            DbDomainQueries domainQueries,
            WebsiteUrl websiteUrl,
            SearchErrorPageService errorPageService,
            BrowseRedirectCommand redirectCommand,
            ConvertCommand convertCommand,
            DefinitionCommand definitionCommand,
            BangCommand bangCommand,
            LangCommand langCommand,
            ScrapeStopCommand scrapeStopCommand,
            SiteRedirectCommand siteRedirectCommand,
            SearchCommand searchCommand
    ) {
        this.domainQueries = domainQueries;
        this.websiteUrl = websiteUrl;
        this.errorPageService = errorPageService;

        specialCommands.add(scrapeStopCommand);
        specialCommands.add(redirectCommand);
        specialCommands.add(convertCommand);
        specialCommands.add(definitionCommand);
        specialCommands.add(bangCommand);
        specialCommands.add(langCommand);
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
            @QueryParam Integer page,
            @QueryParam String sst,
            Context context
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
                    context.getMethod(),
                    null,
                    sst,
                    false,
                    Objects.requireNonNullElse(page,1));

            for (var cmd : specialCommands) {
                var maybe = cmd.process(parameters, context);
                if (maybe.isPresent())
                    return maybe.get();
            }

            return defaultCommand.process(parameters, context).orElseThrow();
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            return errorPageService.serveError(
                    SearchParameters.defaultsForQuery(websiteUrl, query, Objects.requireNonNullElse(page, 1))
            );
        }
    }

    @POST
    @Path("/search")
    public ModelAndView<?> pathSearchPOST(
            @FormParam String query,
            @FormParam String profile,
            @FormParam String js,
            @FormParam String recent,
            @FormParam String searchTitle,
            @FormParam String adtech,
            @FormParam String lang,
            @FormParam String filterSpec,
            @FormParam String sst,
            @FormParam Integer page,
            Context context
    ) {
        try {
            CompiledSearchFilterSpec filter = filterSpecCache.get(filterSpec,
                    () -> {
                        if (filterSpec.isBlank()) {
                            return SearchFilterSpec.defaultForUser("WEB", "AD-HOC").compile(domainQueries);
                        }
                        else {
                            return parser.parse("WEB", "AD-HOC", filterSpec).compile(domainQueries);
                        }
                    });

            SearchParameters parameters = new SearchParameters(websiteUrl,
                    Objects.requireNonNullElse(query, ""),
                    SearchProfile.getSearchProfile(profile),
                    SearchJsParameter.parse(js),
                    SearchRecentParameter.parse(recent),
                    SearchTitleParameter.parse(searchTitle),
                    SearchAdtechParameter.parse(adtech),
                    Objects.requireNonNullElse(lang, "en"),
                    context.getMethod(),
                    filter,
                    sst,
                    false,
                    Objects.requireNonNullElse(page,1));

            for (var cmd : specialCommands) {
                var maybe = cmd.process(parameters, context);
                if (maybe.isPresent())
                    return maybe.get();
            }

            return defaultCommand.process(parameters, context).orElseThrow();
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            return errorPageService.serveError(
                    SearchParameters.defaultsForQuery(websiteUrl, query, Objects.requireNonNullElse(page, 1))
            );
        }
    }
}
