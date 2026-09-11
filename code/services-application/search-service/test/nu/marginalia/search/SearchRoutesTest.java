package nu.marginalia.search;

import io.jooby.Jooby;
import nu.marginalia.search.svc.*;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class SearchRoutesTest {
    @Test
    void validateRoutes() throws Exception {
        var app = new Jooby();
        app.install(new SearchFrontPageServiceHtmx_(mock(SearchFrontPageService.class)));
        app.install(new SearchQueryServiceHtmx_(mock(SearchQueryService.class)));
        app.install(new SearchResultRedirectServiceHtmx_(mock(SearchResultRedirectService.class)));
        app.install(new SearchSiteInfoServiceHtmx_(mock(SearchSiteInfoService.class)));
        app.install(new SearchCrosstalkServiceHtmx_(mock(SearchCrosstalkService.class)));
        app.install(new SearchAddToCrawlQueueServiceHtmx_(mock(SearchAddToCrawlQueueService.class)));
        app.install(new SearchFilterServiceHtmx_(mock(SearchFilterService.class)));
        app.install(new SearchBrowseServiceHtmx_(mock(SearchBrowseService.class)));

        var routes = app.getRoutes().stream()
                .map(route -> route.getMethod() + " " + route.getPattern())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "GET /", "GET /search", "POST /search",
                "GET /r/{node}/{docid}/{timestamp}", "GET /site", "GET /site/{domainName}",
                "POST /site/{domainName}/subscribe", "POST /site/{domainName}/suggest-random",
                "POST /site/{domainName}", "POST /site/suggest/", "GET /crosstalk",
                "GET /filters", "POST /filters", "POST /filters/export",
                "GET /explore", "GET /explore/{domainName}"
        ), routes);
    }
}
