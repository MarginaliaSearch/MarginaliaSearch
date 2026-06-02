package nu.marginalia.search;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.Jooby;
import io.jooby.SessionStore;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import nu.marginalia.service.server.StaticResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SearchService extends JoobyService {

    private final WebsiteUrl websiteUrl;
    private final StaticResources staticResources;
    @org.jetbrains.annotations.NotNull
    private final SearchFrontPageService frontPageService;
    private final SearchErrorPageService errorPageService;
    @org.jetbrains.annotations.NotNull
    private final SearchAddToCrawlQueueService addToCrawlQueueService;
    @org.jetbrains.annotations.NotNull
    private final SearchSiteInfoService siteInfoService;
    @org.jetbrains.annotations.NotNull
    private final SearchCrosstalkService crosstalkService;
    private final SearchQueryService searchQueryService;

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final Histogram wmsa_search_service_request_time = Histogram.builder()
            .name("wmsa_search_service_request_time")
            .classicLinearUpperBounds(0.05, 0.05, 15)
            .labelNames("matchedPath", "method")
            .help("Search service request time (seconds)")
            .register();
    private static final Counter wmsa_search_service_error_count = Counter.builder()
            .name("wmsa_search_service_error_count")
            .labelNames("matchedPath", "method")
            .help("Search service error count")
            .register();

    @Inject
    public SearchService(BaseServiceParams params,
                         WebsiteUrl websiteUrl,
                         StaticResources staticResources,
                         SearchFrontPageService frontPageService,
                         SearchErrorPageService errorPageService,
                         SearchAddToCrawlQueueService addToCrawlQueueService,
                         SearchSiteInfoService siteInfoService,
                         SearchCrosstalkService crosstalkService,
                         SearchQueryService searchQueryService)
    throws Exception
    {
        super(params, List.of(), List.of());

        this.websiteUrl = websiteUrl;
        this.staticResources = staticResources;
        this.frontPageService = frontPageService;
        this.errorPageService = errorPageService;
        this.addToCrawlQueueService = addToCrawlQueueService;
        this.siteInfoService = siteInfoService;
        this.crosstalkService = crosstalkService;
        this.searchQueryService = searchQueryService;
    }

    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        jooby.setSessionStore(SessionStore.memory(Cookie.session("marginalia-session")));

        jooby.get("/search", searchQueryService::pathSearch);
        jooby.get("/search/{site}", siteInfoService::handle);
        jooby.post("/search", siteInfoService::handlePost);
        jooby.get("/news.xml", frontPageService::renderNewsFeed);
        jooby.get("/", frontPageService::render);
        jooby.post("/site/suggest/", addToCrawlQueueService::suggestCrawling);
        jooby.get("/site-search/{site}/{query}", this::siteSearchRedir);
        jooby.get("/crosstalk/", crosstalkService::handle);
        jooby.error((ctx,err, c) -> {
            logger.error("Error {}", err);
            errorPageService.serveError(ctx);
        });

    }

    private Object siteSearchRedir(Context ctx) {
        final String site = ctx.path("site").value("");

        final String searchTerms = ctx.path("site").value("");

        final String query = URLEncoder.encode(String.format("%s site:%s", searchTerms, site), StandardCharsets.UTF_8).trim();
        final String profile = ctx.query("profile").value("yolo");

        ctx.sendRedirect(websiteUrl.withPath("search?query="+query+"&profile="+profile));

        return "";
    }

}
