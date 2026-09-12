package nu.marginalia.search;

import com.google.inject.Inject;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jooby.*;
import io.jooby.exception.BadRequestException;
import io.jooby.handler.AssetSource;
import io.jooby.handler.AssetHandler;
import nu.marginalia.search.exceptions.RedirectException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SearchService extends JoobyService {

    private final WebsiteUrl websiteUrl;
    private final SearchFrontPageService frontPageService;
    private final SearchErrorPageService errorPageService;
    private final SearchAddToCrawlQueueService addToCrawlQueueService;
    private final SearchSiteInfoService siteInfoService;
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
        this.frontPageService = frontPageService;
        this.errorPageService = errorPageService;
        this.addToCrawlQueueService = addToCrawlQueueService;
        this.siteInfoService = siteInfoService;
        this.crosstalkService = crosstalkService;
        this.searchQueryService = searchQueryService;
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        jooby.before(ctx -> ctx.setResponseType(MediaType.HTML));

        jooby.get("/search", timed(ctx -> {
            denyPrefetch(ctx);
            return searchQueryService.pathSearch(ctx);
        }));
        jooby.get("/", timed(frontPageService::render));
        jooby.get("/news.xml", timed(frontPageService::renderNewsFeed));
        jooby.post("/site/suggest/", timed(addToCrawlQueueService::suggestCrawling));
        jooby.get("/site-search/{site}/*", timed(this::siteSearchRedir));
        jooby.get("/site/{site}", timed(ctx -> {
            denyPrefetch(ctx);
            return siteInfoService.handle(ctx);
        }));
        jooby.post("/site/{site}", timed(ctx -> {
            denyPrefetch(ctx);
            return siteInfoService.handlePost(ctx);
        }));
        jooby.get("/crosstalk/", timed(crosstalkService::handle));

        var assets = AssetSource.create(getClass().getClassLoader(), "/static/search");
        jooby.assets("/*", assets).setMaxAge(600).setETag(true);
        jooby.assets("/opensearch.xml", new AssetHandler("opensearch.xml", assets))
                .setMaxAge(600).setETag(true)
                .setMediaTypeResolver(asset -> MediaType.valueOf("application/opensearchdescription+xml"));

        jooby.error(RedirectException.class, (ctx, cause, code) ->
                ctx.sendRedirect(((RedirectException) cause).newUrl));
        jooby.error((ctx, cause, code) -> {
            if (code.value() < 500) {
                ctx.setResponseCode(code).send(code.toString());
                return;
            }
            logger.error("Error during processing", cause);
            wmsa_search_service_error_count.labelValues(ctx.getRequestPath(), ctx.getMethod()).inc();
            ctx.setResponseCode(code);
            errorPageService.serveError(ctx);
        });
    }

    private void denyPrefetch(Context ctx) {
        if (!ctx.header("Sec-Purpose").isMissing())
            throw new BadRequestException("Prefetch is not allowed");
    }

    /** Wraps a route with a timer. */
    private static Route.Handler timed(Route.Handler route) {
        return ctx -> {
            try (var timer = wmsa_search_service_request_time
                    .labelValues(ctx.getRoute().getPattern(), ctx.getMethod()).startTimer()) {
                return route.apply(ctx);
            }
        };
    }

    private Object siteSearchRedir(Context ctx) {
        final String site = ctx.path("site").value();
        final String searchTerms = ctx.path("*").value("");

        final String query = URLEncoder.encode(String.format("%s site:%s", searchTerms, site), StandardCharsets.UTF_8).trim();
        final String profile = ctx.query("profile").value("yolo");

        ctx.sendRedirect(websiteUrl.withPath("search?query="+query+"&profile="+profile));

        return "";
    }

}
