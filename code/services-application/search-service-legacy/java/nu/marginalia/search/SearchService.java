package nu.marginalia.search;

import com.google.inject.Inject;
import io.jooby.*;
import io.jooby.exception.StatusCodeException;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SearchService extends JoobyService {

    private final WebsiteUrl websiteUrl;

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

    private final SearchFrontPageService frontPageService;
    private final SearchErrorPageService errorPageService;
    private final SearchAddToCrawlQueueService addToCrawlQueueService;
    private final SearchSiteInfoService siteInfoService;
    private final SearchCrosstalkService crosstalkService;
    private final SearchQueryService searchQueryService;

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

        final String startTimeAttribute = "start-time";

        // Jooby's before() has no path-scoping parameter, so we guard manually.
        jooby.before(ctx -> {
            String path = ctx.getRequestPath();
            if (path.startsWith("/search") || path.startsWith("/site/")) {
                denyPrefetch(ctx);
            }
            ctx.setAttribute(startTimeAttribute, System.nanoTime());
        });

        jooby.after((Context ctx, Object result, Throwable failure) -> {
            if (failure != null) {
                wmsa_search_service_error_count.labelValues(ctx.getRoute().getPattern(), ctx.getMethod()).inc();
            } else {
                Long startTime = ctx.getAttribute(startTimeAttribute);
                if (startTime != null) {
                    wmsa_search_service_request_time
                            .labelValues(ctx.getRoute().getPattern(), ctx.getMethod())
                            .observe((System.nanoTime() - startTime) / 1e9);
                }
            }
        });

        jooby.get("/search", searchQueryService::pathSearch);

        jooby.get("/", frontPageService::render);
        jooby.get("/news.xml", frontPageService::renderNewsFeed);

        jooby.post("/site/suggest/", addToCrawlQueueService::suggestCrawling);

        jooby.get("/site-search/{site}/*", this::siteSearchRedir);

        jooby.get("/site/{site}", siteInfoService::handle);
        jooby.post("/site/{site}", siteInfoService::handlePost);

        jooby.get("/crosstalk/", crosstalkService::handle);

        jooby.error(Exception.class, (ctx, cause, code) -> {
            logger.error("Error during processing", cause);
            wmsa_search_service_error_count.labelValues(ctx.getRequestPath(), ctx.getMethod()).inc();
            ctx.setResponseType(MediaType.html);
            ctx.send(errorPageService.serveError(ctx));
        });
    }

    private void denyPrefetch(Context ctx) {
        if (!ctx.header("Sec-Purpose").isMissing()) {
            throw new StatusCodeException(StatusCode.BAD_REQUEST);
        }
    }

    private Context siteSearchRedir(Context ctx) {
        final String site = ctx.path("site").value();
        final String searchTerms = ctx.path("*").valueOrNull();

        final String query = URLEncoder.encode(
                String.format("%s site:%s", searchTerms == null ? "" : searchTerms, site).trim(),
                StandardCharsets.UTF_8);
        final String profile = ctx.query("profile").value("yolo");

        ctx.sendRedirect(websiteUrl.withPath("search?query=" + query + "&profile=" + profile));
        return ctx;
    }
}
