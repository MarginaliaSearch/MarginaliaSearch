package nu.marginalia.api;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.model.ApiSearchResults;
import nu.marginalia.api.svc.LicenseService;
import nu.marginalia.api.svc.RateLimiterService;
import nu.marginalia.api.svc.ResponseCache;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.SparkService;
import nu.marginalia.service.server.mq.MqRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

public class ApiService extends SparkService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();

    private final ResponseCache responseCache;
    private final LicenseService licenseService;
    private final RateLimiterService rateLimiterService;
    private final ApiSearchOperator searchOperator;

    // Marker for filtering out sensitive content from the persistent logs
    private final Marker queryMarker = MarkerFactory.getMarker("QUERY");

    private final Counter wmsa_api_timeout_count = Counter.build()
            .name("wmsa_api_timeout_count")
            .labelNames("key")
            .help("API timeout count")
            .register();
    private final Counter wmsa_api_cache_hit_count = Counter.build()
            .name("wmsa_api_cache_hit_count")
            .labelNames("key")
            .help("API cache hit count")
            .register();

    private static final Histogram wmsa_api_query_time = Histogram.build()
            .name("wmsa_api_query_time")
            .labelNames("key")
            .linearBuckets(0.05, 0.05, 15)
            .help("API-side query time")
            .register();

    @Inject
    public ApiService(BaseServiceParams params,
                      ResponseCache responseCache,
                      LicenseService licenseService,
                      RateLimiterService rateLimiterService,
                      ApiSearchOperator searchOperator)
    throws Exception
    {

        super(params);

        this.responseCache = responseCache;
        this.licenseService = licenseService;
        this.rateLimiterService = rateLimiterService;
        this.searchOperator = searchOperator;

        Spark.get("/api/", (rq, rsp) -> {
            rsp.redirect("https://about.marginalia-search.com/article/api/");
            return "";
        });

        Spark.get("/api/:key", (rq, rsp) -> licenseService.getLicense(rq.params("key")), gson::toJson);
        Spark.get("/api/:key/", (rq, rsp) -> licenseService.getLicense(rq.params("key")), gson::toJson);

        Spark.get("/api/:key/search/*", this::search, gson::toJson);
    }

    @MqRequest(endpoint = "FLUSH_CACHES")
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

        response.type("application/json");

        // Check if we have a cached response
        var cachedResponse = responseCache.getResults(license, args[0], request.queryString());
        if (cachedResponse.isPresent()) {
            wmsa_api_cache_hit_count.labels(license.key).inc();
            return cachedResponse.get();
        }

        // When no cached response, do the search and cache the result
        var result = doSearch(license, args[0], request);
        responseCache.putResults(license, args[0], request.queryString(), result);
        return result;
    }

    private ApiSearchResults doSearch(ApiLicense license, String query, Request request) {
        if (!rateLimiterService.isAllowed(license)) {
            wmsa_api_timeout_count.labels(license.key).inc();
            Spark.halt(503, "Slow down");
        }

        int count = intParam(request, "count", 20);
        int index = intParam(request, "index", 3);

        logger.info(queryMarker, "{} Search {}", license.key, query);

        return wmsa_api_query_time
                .labels(license.key)
                .time(() ->
                        searchOperator
                        .query(query, count, index)
                        .withLicense(license.getLicense())
                );
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
