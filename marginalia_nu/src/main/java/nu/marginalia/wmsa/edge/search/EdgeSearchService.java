package nu.marginalia.wmsa.edge.search;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.api.model.ApiSearchResult;
import nu.marginalia.wmsa.api.model.ApiSearchResults;
import nu.marginalia.wmsa.client.exception.TimeoutException;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.search.command.CommandEvaluator;
import nu.marginalia.wmsa.edge.search.command.ResponseType;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class EdgeSearchService extends Service {

    private final EdgeIndexClient indexClient;
    private final EdgeSearchOperator searchOperator;
    private final CommandEvaluator searchCommandEvaulator;

    private static final Logger logger = LoggerFactory.getLogger(EdgeSearchService.class);

    @SneakyThrows
    @Inject
    public EdgeSearchService(@Named("service-host") String ip,
                             @Named("service-port") Integer port,
                             EdgeIndexClient indexClient,
                             Initialization initialization,
                             MetricsServer metricsServer,
                             EdgeSearchOperator searchOperator,
                             CommandEvaluator searchCommandEvaulator
                             ) {
        super(ip, port, initialization, metricsServer);
        this.indexClient = indexClient;

        this.searchOperator = searchOperator;
        this.searchCommandEvaulator = searchCommandEvaulator;

        Spark.staticFiles.expireTime(600);

        Spark.get("/search", this::pathSearch);

        Gson gson = new GsonBuilder().create();

        Spark.get("/api/search", this::apiSearch, gson::toJson);
        Spark.get("/public/search", this::pathSearch);
        Spark.get("/site-search/:site/*", this::siteSearchRedir);
        Spark.get("/public/site-search/:site/*", this::siteSearchRedir);

        Spark.exception(Exception.class, (e,p,q) -> {
            logger.error("Error during processing", e);
            serveError(Context.fromRequest(p), q);
        });

        Spark.awaitInitialization();
    }

    private Object siteSearchRedir(Request request, Response response) {
        final String site = request.params("site");
        final String queryRaw = request.splat()[0];

        final String query = URLEncoder.encode(String.format("%s site:%s", queryRaw, site), StandardCharsets.UTF_8);
        final String profile = request.queryParamOrDefault("profile", "yolo");

        response.redirect("https://search.marginalia.nu/search?query="+query+"&profile="+profile);

        return null;
    }


    private void serveError(Context ctx, Response rsp) {
        boolean isIndexUp = indexClient.isAlive();

        try {
            if (!isIndexUp) {
                rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"> <meta http-equiv=\"refresh\" content=\"5\"> </head><body><article><h1>Error</h1><p>Oops! It appears the index server is <span class=\"headline\">offline</span>.</p> <p>The server was probably restarted to bring online some changes. Restarting the index typically takes a few minutes, during which searches can't be served. </p><p>This page will attempt to refresh automatically every few seconds.</p></body></html>");
            } else if (indexClient.isBlocked(ctx).blockingFirst()) {
                rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"> <meta http-equiv=\"refresh\" content=\"5\"> </head><body><article><h1>Error</h1><p>Oops! It appears the index server is <span class=\"headline\">starting up</span>.</p> <p>The server was probably restarted to bring online some changes. Restarting the index typically takes a few minutes, during which searches can't be served. </p><p>This page will attempt to refresh automatically every few seconds.</p></body></html>");
            }
            else {
                rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"></head><body><article><h1>Error</h1><p>Oops! An unknown error occurred. The index server seems to be up, so I don't know why this is. Please send an email to kontakt@marginalia.nu telling me what you did :-) </p></body></html>");
            }
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            rsp.body("<html><head><title>Error</title><link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"> <meta http-equiv=\"refresh\" content=\"5\"> </head><body><article><h1>Error</h1><p>Oops! It appears the index server is <span class=\"headline\">unresponsive</span>.</p> <p>The server was probably restarted to bring online some changes. Restarting the index typically takes a few minutes, during which searches can't be served. </p><p>This page will attempt to refresh automatically every few seconds.</p></body></html>");
        }

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

        var results = searchOperator.doApiSearch(ctx, new EdgeUserSearchParameters(humanQuery, profile, ""));

        return new ApiSearchResults("RESTRICTED", humanQuery, results.stream().map(ApiSearchResult::new).limit(limit).collect(Collectors.toList()));
    }

    @SneakyThrows
    private Object pathSearch(Request request, Response response) {

        final var ctx = Context.fromRequest(request);

        final String queryParam = request.queryParams("query");
        if (null == queryParam || queryParam.isBlank()) {
            response.redirect("https://search.marginalia.nu/");
            return null;
        }

        final String profileStr = Optional.ofNullable(request.queryParams("profile")).orElse("yolo");
        final String humanQuery = queryParam.trim();
        final String format = request.queryParams("format");
        ResponseType responseType;

        if ("gmi".equals(format)) {
            response.type("text/gemini");
            responseType = ResponseType.GEMINI;
        }
        else {
            responseType = ResponseType.HTML;
        }

        var params = new SearchParameters(
                EdgeSearchProfile.getSearchProfile(profileStr),
                Optional.ofNullable(request.queryParams("js")).orElse("default"),
                responseType);
        try {
            return searchCommandEvaulator.eval(ctx, params, humanQuery);
        }
        catch (Exception ex) {
            logger.error("Error", ex);
            serveError(ctx, response);
        }

        return "";
    }


}
