package nu.marginalia.search;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.client.Context;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.search.command.IndexCommand;
import nu.marginalia.search.svc.*;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.service.server.Service;
import nu.marginalia.service.server.StaticResources;
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
    public SearchService(@Named("service-host") String ip,
                         @Named("service-port") Integer port,
                         Initialization initialization,
                         MetricsServer metricsServer,
                         WebsiteUrl websiteUrl,
                         StaticResources staticResources,
                         IndexCommand indexCommand,
                         SearchErrorPageService errorPageService,
                         SearchAddToCrawlQueueService addToCrawlQueueService,
                         SearchFlagSiteService flagSiteService,
                         SearchQueryService searchQueryService,
                         SearchApiQueryService apiQueryService
                             ) {
        super(ip, port, initialization, metricsServer);

        this.websiteUrl = websiteUrl;
        this.staticResources = staticResources;

        Spark.staticFiles.expireTime(600);

        Spark.get("/search", searchQueryService::pathSearch);

        Gson gson = GsonFactory.get();

        Spark.get("/api/search", apiQueryService::apiSearch, gson::toJson);
        Spark.get("/public/search", searchQueryService::pathSearch);
        Spark.get("/public/site-search/:site/*", this::siteSearchRedir);
        Spark.get("/public/", indexCommand::render);
        Spark.get("/public/:resource", this::serveStatic);

        Spark.post("/public/site/suggest/", addToCrawlQueueService::suggestCrawling);

        Spark.get("/public/site/flag-site/:domainId", flagSiteService::flagSiteForm);
        Spark.post("/public/site/flag-site/:domainId", flagSiteService::flagSiteAction);

        Spark.get("/site-search/:site/*", this::siteSearchRedir);


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
        final String queryRaw = request.splat()[0];

        final String query = URLEncoder.encode(String.format("%s site:%s", queryRaw, site), StandardCharsets.UTF_8);
        final String profile = request.queryParamOrDefault("profile", "yolo");

        response.redirect(websiteUrl.withPath("search?query="+query+"&profile="+profile));

        return null;
    }



}
