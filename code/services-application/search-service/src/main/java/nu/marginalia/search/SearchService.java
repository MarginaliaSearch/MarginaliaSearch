package nu.marginalia.search;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.client.Context;
import nu.marginalia.search.svc.SearchFrontPageService;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SearchService extends Service {

    private final WebsiteUrl websiteUrl;
    private final StaticResources staticResources;

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    @SneakyThrows
    @Inject
    public SearchService(BaseServiceParams params,
                         WebsiteUrl websiteUrl,
                         StaticResources staticResources,
                         SearchFrontPageService frontPageService,
                         SearchErrorPageService errorPageService,
                         SearchAddToCrawlQueueService addToCrawlQueueService,
                         SearchSiteInfoService siteInfoService,
                         SearchQueryService searchQueryService
                             ) {
        super(params);

        this.websiteUrl = websiteUrl;
        this.staticResources = staticResources;

        Spark.staticFiles.expireTime(600);

        Spark.get("/search", searchQueryService::pathSearch);

        Spark.get("/public/search", searchQueryService::pathSearch);
        Spark.get("/public/", frontPageService::render);
        Spark.get("/public/news.xml", frontPageService::renderNewsFeed);
        Spark.get("/public/:resource", this::serveStatic);

        Spark.post("/public/site/suggest/", addToCrawlQueueService::suggestCrawling);

        Spark.get("/public/site-search/:site/*", this::siteSearchRedir);

        Spark.get("/public/site/:site", siteInfoService::handle);
        Spark.post("/public/site/:site", siteInfoService::handlePost);

        Spark.exception(Exception.class, (e,p,q) -> {
            logger.error("Error during processing", e);
            errorPageService.serveError(Context.fromRequest(p), p, q);
        });

        Spark.awaitInitialization();
    }

    private Object serveStatic(Request request, Response response) {
        String resource = request.params("resource");
        staticResources.serveStatic("search", resource, request, response);
        return "";
    }

    private Object siteSearchRedir(Request request, Response response) {
        final String site = request.params("site");
        final String searchTerms;

        if (request.splat().length == 0) searchTerms = "";
        else searchTerms = request.splat()[0];

        final String query = URLEncoder.encode(String.format("%s site:%s", searchTerms, site), StandardCharsets.UTF_8).trim();
        final String profile = request.queryParamOrDefault("profile", "yolo");

        response.redirect(websiteUrl.withPath("search?query="+query+"&profile="+profile));

        return "";
    }

}
