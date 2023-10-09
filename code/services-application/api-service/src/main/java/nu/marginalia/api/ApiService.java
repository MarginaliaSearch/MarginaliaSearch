package nu.marginalia.api;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.model.ApiSearchResults;
import nu.marginalia.api.svc.LicenseService;
import nu.marginalia.api.svc.RateLimiterService;
import nu.marginalia.api.svc.ResponseCache;
import nu.marginalia.client.Context;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.query.client.QueryClient;
import nu.marginalia.service.server.*;
import nu.marginalia.service.server.mq.MqNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

public class ApiService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();
    private final QueryClient queryClient;

    private final ResponseCache responseCache;
    private final LicenseService licenseService;
    private final RateLimiterService rateLimiterService;
    private final ApiSearchOperator searchOperator;

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    @Inject
    public ApiService(BaseServiceParams params,
                      QueryClient queryClient,
                      ResponseCache responseCache,
                      LicenseService licenseService,
                      RateLimiterService rateLimiterService,
                      ApiSearchOperator searchOperator
                      ) {

        super(params);

        this.queryClient = queryClient;
        this.responseCache = responseCache;
        this.licenseService = licenseService;
        this.rateLimiterService = rateLimiterService;
        this.searchOperator = searchOperator;

        Spark.get("/public/api/", (rq, rsp) -> {
            rsp.redirect("https://memex.marginalia.nu/projects/edge/api.gmi");
            return "";
        });

        Spark.get("/public/api/:key", (rq, rsp) -> licenseService.getLicense(rq.params("key")), gson::toJson);
        Spark.get("/public/api/:key/", (rq, rsp) -> licenseService.getLicense(rq.params("key")), gson::toJson);

        Spark.get("/public/api/:key/search/*", this::search, gson::toJson);
    }

    @MqNotification(endpoint = "FLUSH_CACHES")
    public void flushCaches(String unusedArg) {
        logger.info("Flushing caches");

        responseCache.flush();
        licenseService.flushCache();
    }

    private Object search(Request request, Response response) {

        String[] args = request.splat();
        if (args.length != 1) {
            Spark.halt(400, "Bad request");
        }

        var license = licenseService.getLicense(request.params("key"));

        var cachedResponse = responseCache.getResults(license, args[0], request.queryString());
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        var result = doSearch(license, args[0], request);
        responseCache.putResults(license, args[0], request.queryString(), result);

        // We set content type late because in the case of error, we don't want to tell the client
        // that the error message  is JSON when it is plain text.

        response.type("application/json");

        return result;
    }

    private ApiSearchResults doSearch(ApiLicense license, String query, Request request) {
        if (!rateLimiterService.isAllowed(license)) {
            Spark.halt(503, "Slow down");
        }

        int count = intParam(request, "count", 20);
        int index = intParam(request, "index", 3);

        logger.info(queryMarker, "{} Search {}", license.key, query);

        return searchOperator
                .query(Context.fromRequest(request), query, count, index)
                .withLicense(license.getLicense());
    }

    private int intParam(Request request, String name, int defaultValue) {
        var value = request.queryParams(name);

        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ex) {
            Spark.halt(400, "Invalid parameter value for " + name);

            return defaultValue;
        }
    }


}
