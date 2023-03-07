package nu.marginalia.api;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.client.Context;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.search.client.SearchClient;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.service.server.RateLimiter;
import nu.marginalia.service.server.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.concurrent.ConcurrentHashMap;

public class ApiService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = GsonFactory.get();
    private final SearchClient searchClient;
    private final HikariDataSource dataSource;
    private final ConcurrentHashMap<String, ApiLicense> licenseCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ApiLicense, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    @Inject
    public ApiService(@Named("service-host") String ip,
                      @Named("service-port") Integer port,
                      Initialization initialization,
                      MetricsServer metricsServer,
                      SearchClient searchClient,
                      HikariDataSource dataSource) {
        super(ip, port, initialization, metricsServer);
        this.searchClient = searchClient;
        this.dataSource = dataSource;

        Spark.get("/public/api/", (rq, rsp) -> {
            logger.info("Redireting to info");
            rsp.redirect("https://memex.marginalia.nu/projects/edge/api.gmi");
            return "";
        });
        Spark.get("/public/api/:key/", this::getKeyInfo, gson::toJson);
        Spark.get("/public/api/:key/search/*", this::search, gson::toJson);
    }

    private Object getKeyInfo(Request request, Response response) {
        return getLicense(request);
    }

    private Object search(Request request, Response response) {
        response.type("application/json");

        String[] args = request.splat();
        if (args.length != 1) {
            Spark.halt(400);
        }

        var license = getLicense(request);
        if (null == license) {
            Spark.halt(401);
            return "Forbidden";
        }

        RateLimiter rl = getRateLimiter(license);

        if (rl != null && !rl.isAllowed()) {
            Spark.halt(503);
            return "Slow down";
        }

        int count = Integer.parseInt(request.queryParamOrDefault("count", "20"));
        int index = Integer.parseInt(request.queryParamOrDefault("index", "3"));

        logger.info("{} Search {}", license.key, args[0]);

        return searchClient.query(Context.fromRequest(request), args[0], count, index)
                .blockingFirst().withLicense(license.getLicense());
    }

    private RateLimiter getRateLimiter(ApiLicense license) {
        if (license.rate > 0) {
            return rateLimiters.computeIfAbsent(license, l -> RateLimiter.custom(license.rate));
        }
        else {
            return null;
        }
    }


    private ApiLicense getLicense(Request request) {
        final String key = request.params("key");

        if (Strings.isNullOrEmpty(key)) {
            Spark.halt(400);
        }

        var cachedLicense = licenseCache.get(key.toLowerCase());
        if (cachedLicense != null) {
            return cachedLicense;
        }

        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("SELECT LICENSE,NAME,RATE FROM EC_API_KEY WHERE LICENSE_KEY=?")) {
                stmt.setString(1, key);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    var license = new ApiLicense(key.toLowerCase(), rsp.getString(1), rsp.getString(2), rsp.getInt(3));
                    licenseCache.put(key.toLowerCase(), license);
                    return license;
                }
            }
        }
        catch (Exception ex) {
            logger.error("Bad request", ex);
            Spark.halt(500);
        }

        Spark.halt(401);
        return null; // unreachable
    }
}
