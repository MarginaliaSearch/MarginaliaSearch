package nu.marginalia.search;

import com.google.inject.Inject;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import nu.marginalia.service.server.StaticResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SearchService extends JoobyService {

    private final WebsiteUrl websiteUrl;
    private final StaticResources staticResources;

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final Histogram wmsa_search_service_request_time = Histogram.build()
            .name("wmsa_search_service_request_time")
            .linearBuckets(0.05, 0.05, 15)
            .labelNames("matchedPath", "method")
            .help("Search service request time (seconds)")
            .register();
    private static final Counter wmsa_search_service_error_count = Counter.build()
            .name("wmsa_search_service_error_count")
            .labelNames("matchedPath", "method")
            .help("Search service error count")
            .register();

    @Inject
    public SearchService(BaseServiceParams params,
                         WebsiteUrl websiteUrl,
                         StaticResources staticResources,
                         SearchFrontPageService frontPageService,
                         SearchAddToCrawlQueueService addToCrawlQueueService,
                         SearchSiteInfoService siteInfoService,
                         SearchCrosstalkService crosstalkService,
                         SearchBrowseService searchBrowseService,
                         SearchQueryService searchQueryService)
    throws Exception {
        super(params,
                ServicePartition.any(),
                List.of(),
                List.of(new SearchFrontPageService_(frontPageService),
                        new SearchQueryService_(searchQueryService),
                        new SearchSiteInfoService_(siteInfoService),
                        new SearchCrosstalkService_(crosstalkService),
                        new SearchAddToCrawlQueueService_(addToCrawlQueueService),
                        new SearchBrowseService_(searchBrowseService)
                ));

        this.websiteUrl = websiteUrl;
        this.staticResources = staticResources;

    }
//
//        SearchServiceMetrics.get("/search", searchQueryService::pathSearch);
//        SearchServiceMetrics.get("/", frontPageService::render);
//        SearchServiceMetrics.get("/news.xml", frontPageService::renderNewsFeed);
//
//        SearchServiceMetrics.post("/site/suggest/", addToCrawlQueueService::suggestCrawling);
//
//        SearchServiceMetrics.get("/site-search/:site/*", this::siteSearchRedir);
//
//        SearchServiceMetrics.get("/site", siteInfoService::handleOverview);
//        SearchServiceMetrics.get("/site/:site", siteInfoService::handle);
//        SearchServiceMetrics.post("/site/:site", siteInfoService::handlePost);
//
//        SearchServiceMetrics.get("/explore", searchBrowseService::handleBrowseRandom);
//        SearchServiceMetrics.get("/explore/:site", searchBrowseService::handleBrowseSite);
//
//        SearchServiceMetrics.get("/crosstalk/", crosstalkService::handle);
//
//        SearchServiceMetrics.get("/:resource", this::serveStatic);
//        Spark.exception(Exception.class, (e,p,q) -> {
//            logger.error("Error during processing", e);
//            wmsa_search_service_error_count.labels(p.pathInfo(), p.requestMethod()).inc();
//            errorPageService.serveError(p, q);
//        });
//
//        // Add compression
//        Spark.after((rq, rs) -> {
//            rs.header("Content-Encoding", "gzip");
//        });
//
//        Spark.awaitInitialization();

//
//
//    /** Wraps a route with a timer and a counter */
//    private static class SearchServiceMetrics implements Route {
//        private final Route delegatedRoute;
//
//        static void get(String path, Route route) {
//            Spark.get(path, new SearchServiceMetrics(route));
//        }
//        static void post(String path, Route route) {
//            Spark.post(path, new SearchServiceMetrics(route));
//        }
//
//        private SearchServiceMetrics(Route delegatedRoute) {
//            this.delegatedRoute = delegatedRoute;
//        }
//
//        @Override
//        public Object handle(Request request, Response response) throws Exception {
//            return wmsa_search_service_request_time
//                    .labels(request.matchedPath(), request.requestMethod())
//                    .time(() -> delegatedRoute.handle(request, response));
//        }
//    }
//
//    private Object serveStatic(Request request, Response response) {
//        String resource = request.params("resource");
//        staticResources.serveStatic("search", resource, request, response);
//        return "";
//    }
//
//    private Object siteSearchRedir(Request request, Response response) {
//        final String site = request.params("site");
//        final String searchTerms;
//
//        if (request.splat().length == 0) searchTerms = "";
//        else searchTerms = request.splat()[0];
//
//        final String query = URLEncoder.encode(String.format("%s site:%s", searchTerms, site), StandardCharsets.UTF_8).trim();
//        final String profile = request.queryParamOrDefault("profile", "yolo");
//
//        response.redirect(websiteUrl.withPath("search?query="+query+"&profile="+profile));
//
//        return "";
//    }

}
