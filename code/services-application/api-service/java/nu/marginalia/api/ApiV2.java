package nu.marginalia.api;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.Value;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.api.model.ApiLicenseOptions;
import nu.marginalia.api.model.ApiSearchResults;
import nu.marginalia.api.model.DailyLimitState;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.svc.FiltersService;
import nu.marginalia.api.svc.LicenseService;
import nu.marginalia.api.svc.RateLimiterService;
import nu.marginalia.api.svc.ResponseCache;
import nu.marginalia.model.gson.GsonFactory;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

public class ApiV2 {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Marker apiUsageMarker = MarkerFactory.getMarker("APIUSAGE");

    private final Gson gson = GsonFactory.get();
    private final ResponseCache responseCache;
    private final LicenseService licenseService;
    private final RateLimiterService rateLimiterService;
    private final ApiSearchOperator searchOperator;
    private final QueryClient queryClient;
    private final FiltersService filtersService;

    @Inject
    public ApiV2(ResponseCache responseCache,
                 LicenseService licenseService,
                 RateLimiterService rateLimiterService,
                 ApiSearchOperator searchOperator,
                 QueryClient queryClient,
                 FiltersService filtersService)
    {
        this.responseCache = responseCache;
        this.licenseService = licenseService;
        this.rateLimiterService = rateLimiterService;
        this.searchOperator = searchOperator;
        this.queryClient = queryClient;
        this.filtersService = filtersService;
    }

    public void registerApi(Jooby jooby) {
        jooby.get("/api/v2/", ctx -> {
            ctx.sendRedirect(StatusCode.TEMPORARY_REDIRECT, "https://about.marginalia-search.com/article/api/");
            return ctx;
        });

        jooby.errorCode(LicenseService.NoSuchKeyException.class, StatusCode.UNAUTHORIZED);

        jooby.get("/api/v2/key", this::keyInfo);
        jooby.get("/api/v2/search", this::search);

        jooby.get("/api/v2/filter", this::listFilters);

        jooby.get("/api/v2/filter/{id}", this::getFilter);
        jooby.post("/api/v2/filter/{id}", this::updateFilter);
        jooby.delete("/api/v2/filter/{id}", this::deleteFilter);
    }

    @NotNull
    private Object listFilters(Context ctx) {
        Value key = ctx.header("API-Key");

        if (key.isMissing()) {
            ctx.setResponseCode(400);
            return "";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(key.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        ctx.setResponseType("application/json");
        return gson.toJson(filtersService.listFilters(license));
    }

    @NotNull
    private Object getFilter(Context ctx) {
        Value key = ctx.header("API-Key");

        if (key.isMissing()) {
            ctx.setResponseCode(400);
            return "";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(key.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        Value filterId = ctx.path("id");
        if (filterId.isMissing()) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            return "";
        }

        var filterMaybe = filtersService.getFilter(license, filterId.value());
        if (filterMaybe.isEmpty()) {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
            return "No such filter";
        }

        ctx.setResponseType("application/xml");
        return filterMaybe.get();
    }

    @NotNull
    private Object updateFilter(Context ctx) throws SQLException {
        Value key = ctx.header("API-Key");

        if (key.isMissing()) {
            ctx.setResponseCode(400);
            return "";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(key.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        if ("PUBLIC".equalsIgnoreCase(license.key())) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        Value filterIdVal = ctx.path("id");
        if (filterIdVal.isMissing()) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            return "";
        }

        String filterId = filterIdVal.value();
        if (!filterId.matches("[a-zA-Z\\-_0-9]+")) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            return "Filter ID should match [a-zA-Z\\-_0-9]+";
        }

        // Apply rate limit to filter updates as this isn't zero cost
        if (!rateLimiterService.isAllowedQPM(license)) {
            ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
            ctx.setResponseCode(503);
            return ctx;
        }

        String filterDefinition = ctx.body().value();

        var problems = filtersService.updateFilter(license, filterId, filterDefinition);
        if (!problems.isEmpty()) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            return Strings.join(problems, '\n');
        }

        // There's a small chance we failed to invalidate the caches,
        // report as FAILED_DEPENDENCY in this scenario
        if (!queryClient.invalidateFilterCache(license.key(), filterId)) {
            ctx.setResponseCode(StatusCode.FAILED_DEPENDENCY);
            return """
            The database has been updated, but query-service cache invalidation failed,
            as a result you may see stale filter data until internal cache expires.
            
            Repeating the attempted request could help flush the caches.
            """;
        }
        else {
            ctx.setResponseCode(StatusCode.ACCEPTED);
        }

        return "";
    }

    @NotNull
    private Object deleteFilter(Context ctx) throws SQLException {
        Value key = ctx.header("API-Key");

        if (key.isMissing()) {
            ctx.setResponseCode(400);
            return "";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(key.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        if ("PUBLIC".equalsIgnoreCase(license.key())) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        Value filterId = ctx.path("id");
        if (filterId.isMissing()) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            return "";
        }

        filtersService.deleteFilter(license, filterId.value());

        // We won't bother checking whether this went through as it'll clear in a while automatically
        queryClient.invalidateFilterCache(license.key(), filterId.value());

        ctx.setResponseCode(StatusCode.ACCEPTED);
        return "";
    }


    @NotNull
    private Object keyInfo(Context ctx) {
        Value key = ctx.header("API-Key");

        if (key.isMissing()) {
            ctx.setResponseCode(400);
            return "";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(key.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        ctx.setResponseType("application/json");
        return gson.toJson(license);
    }

    @NotNull
    private Object search(Context ctx) {
        Value apiKeyVal = ctx.header("API-Key");
        if (apiKeyVal.isMissing()) {
            ctx.setResponseCode(400);
            return "";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(apiKeyVal.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }


        Value queryVal = ctx.query("query");
        if (!queryVal.isPresent()) {
            ctx.setResponseCode(400);
            return "";
        }
        String query = queryVal.value();


        ApiSearchResults results;
        DailyLimitState limitState;

        // Check if we have a cached response
        var cachedResponse = responseCache.getResults(license, query, ctx.queryString());

        if (cachedResponse.isPresent()) {
            ApiMetrics.wmsa_api_cache_hit_count.labelValues(license.key()).inc();

            results = cachedResponse.get();

            int remaining = rateLimiterService.remainingDailyLimit(license);
            limitState = new DailyLimitState.Cached(remaining);
        }
        else {
            if (!rateLimiterService.isAllowedQPM(license)) {
                ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
                ctx.setResponseCode(429);

                return "QPM Limit Exceeded";
            }

            if (!rateLimiterService.isAllowedQPD(license)) {
                ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
                ctx.setResponseCode(429);

                limitState = new DailyLimitState.OverLimitBlock();

                ctx.setResponseHeader("API-Remaining-Daily-Capacity", limitState.remaining());
                ctx.setResponseHeader("API-Event-Type", limitState.name());

                return "Daily Limit Exceeded";
            }

            // When no cached response, do the search and cache the result
            results = doSearch(license, query, ctx);

            if (results == null)
                return null;

            if (results.getResults().size() > 0) {
                limitState = rateLimiterService.registerSuccessfulQuery(license);
            } else {
                int remaining = rateLimiterService.remainingDailyLimit(license);
                limitState = new DailyLimitState.NoResults(remaining);
            }

            responseCache.putResults(license, query, ctx.queryString(), results);
        }

        if (license.hasOption(ApiLicenseOptions.ADUIT_USAGE)) {
            logger.info(apiUsageMarker, "{} {} {}", license.key(), limitState, ctx.header("X-Forwarded-For"));
        }

        ctx.setResponseType("application/json");
        ctx.setResponseHeader("API-Remaining-Daily-Capacity", limitState.remaining());
        ctx.setResponseHeader("API-Event-Type", limitState.name());

        return gson.toJson(results);
    }

    private ApiSearchResults doSearch(ApiLicense license, String query, Context context) {
        int count = context.query().get("count").intValue(20);
        int domainCount = context.query().get("dc").intValue(2);
        int timeout = context.query().get("timeout").intValue(150);
        String filterName = context.query().get("filter").valueOrNull();
        int nsfw = context.query().get("nsfw").intValue(1);
        String langIsoCode = context.query("lang").value("en");

        NsfwFilterTier nsfwFilterTier;
        try {
            nsfwFilterTier = NsfwFilterTier.fromCodedValue(nsfw);
        }
        catch (IllegalArgumentException e) {
            context.setResponseCode(400);
            return null;
        }

        final QueryFilterSpec filter;

        if (filterName == null)
            filter = SearchFilterDefaults.NO_FILTER.asFilterSpec();
        else
            filter = new QueryFilterSpec.FilterByName(license.key(), filterName);

        try (var _ = ApiMetrics.wmsa_api_query_time.labelValues(license.key()).startTimer())
        {
            return searchOperator
                    .v2query(query, count, timeout, domainCount, filter, nsfwFilterTier, langIsoCode, license);
        }
        catch (TimeoutException ex) {
            context.setResponseCode(504);
            return null;
        }
    }
}
