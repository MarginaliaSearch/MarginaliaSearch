package nu.marginalia.wmsa.edge.search;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.api.model.ApiSearchResult;
import nu.marginalia.wmsa.api.model.ApiSearchResults;
import nu.marginalia.wmsa.client.GsonFactory;
import nu.marginalia.wmsa.configuration.WebsiteUrl;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.search.command.CommandEvaluator;
import nu.marginalia.wmsa.edge.search.command.IndexCommand;
import nu.marginalia.wmsa.edge.search.command.SearchJsParameter;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.exceptions.RedirectException;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.edge.search.svc.EdgeSearchErrorPageService;
import nu.marginalia.wmsa.resource_store.StaticResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

public class EdgeSearchService extends Service {

    private final EdgeIndexClient indexClient;
    private final EdgeSearchOperator searchOperator;
    private final CommandEvaluator searchCommandEvaulator;
    private final WebsiteUrl websiteUrl;
    private StaticResources staticResources;

    private final EdgeSearchErrorPageService errorPageService;
    private static final Logger logger = LoggerFactory.getLogger(EdgeSearchService.class);

    @SneakyThrows
    @Inject
    public EdgeSearchService(@Named("service-host") String ip,
                             @Named("service-port") Integer port,
                             EdgeIndexClient indexClient,
                             Initialization initialization,
                             MetricsServer metricsServer,
                             EdgeSearchOperator searchOperator,
                             CommandEvaluator searchCommandEvaulator,
                             WebsiteUrl websiteUrl,
                             StaticResources staticResources,
                             IndexCommand indexCommand,
                             EdgeSearchErrorPageService errorPageService) {
        super(ip, port, initialization, metricsServer);
        this.indexClient = indexClient;

        this.searchOperator = searchOperator;
        this.searchCommandEvaulator = searchCommandEvaulator;
        this.websiteUrl = websiteUrl;
        this.staticResources = staticResources;
        this.errorPageService = errorPageService;

        Spark.staticFiles.expireTime(600);

        Spark.get("/search", this::pathSearch);

        Gson gson = GsonFactory.get();

        Spark.get("/api/search", this::apiSearch, gson::toJson);
        Spark.get("/public/search", this::pathSearch);
        Spark.get("/public/site-search/:site/*", this::siteSearchRedir);
        Spark.get("/public/", indexCommand::render);
        Spark.get("/public/:resource", this::serveStatic);

        Spark.get("/site-search/:site/*", this::siteSearchRedir);


        Spark.exception(Exception.class, (e,p,q) -> {
            logger.error("Error during processing", e);
            errorPageService.serveError(Context.fromRequest(p), q);
        });

        Spark.awaitInitialization();
    }

    private Object serveStatic(Request request, Response response) {
        String resource = request.params("resource");
        staticResources.serveStatic("edge", resource, request, response);
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


    @SneakyThrows
    private Object apiSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);
        final String queryParam = request.queryParams("query");
        final int limit;
        EdgeSearchProfile profile = EdgeSearchProfile.YOLO;

        String count = request.queryParamOrDefault("count", "20");
        limit = Integer.parseInt(count);

        String index = request.queryParamOrDefault("index", "0");
        if (!Strings.isNullOrEmpty(index)) {
            profile = switch (index) {
                case "0" -> EdgeSearchProfile.YOLO;
                case "1" -> EdgeSearchProfile.MODERN;
                case "2" -> EdgeSearchProfile.DEFAULT;
                case "3" -> EdgeSearchProfile.CORPO_CLEAN;
                default -> EdgeSearchProfile.CORPO_CLEAN;
            };
        }

        final String humanQuery = queryParam.trim();

        var results = searchOperator.doApiSearch(ctx, new EdgeUserSearchParameters(humanQuery, profile, SearchJsParameter.DEFAULT));

        return new ApiSearchResults("RESTRICTED", humanQuery, results.stream().map(ApiSearchResult::new).limit(limit).collect(Collectors.toList()));
    }

    @SneakyThrows
    private Object pathSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);

        final String queryParam = request.queryParams("query");
        if (null == queryParam || queryParam.isBlank()) {
            response.redirect(websiteUrl.url());
            return null;
        }

        final String profileStr = Optional.ofNullable(request.queryParams("profile")).orElse(EdgeSearchProfile.YOLO.name);
        final String humanQuery = queryParam.trim();

        var params = new SearchParameters(
                EdgeSearchProfile.getSearchProfile(profileStr),
                SearchJsParameter.parse(request.queryParams("js"))
        );

        try {
            return searchCommandEvaulator.eval(ctx, params, humanQuery);
        }
        catch (RedirectException ex) {
            response.redirect(ex.newUrl);
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            errorPageService.serveError(ctx, response);
        }

        return "";
    }


}
