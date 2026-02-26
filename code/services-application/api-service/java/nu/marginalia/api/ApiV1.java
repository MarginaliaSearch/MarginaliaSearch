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
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.svc.LicenseService;
import nu.marginalia.api.svc.RateLimiterService;
import nu.marginalia.api.svc.ResponseCache;
import nu.marginalia.model.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ApiV1 {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Gson gson = GsonFactory.get();
    private final ResponseCache responseCache;
    private final LicenseService licenseService;
    private final RateLimiterService rateLimiterService;
    private final ApiSearchOperator searchOperator;

    @Inject
    public ApiV1(ResponseCache responseCache,
                 LicenseService licenseService,
                 RateLimiterService rateLimiterService,
                 ApiSearchOperator searchOperator)
    {
        this.responseCache = responseCache;
        this.licenseService = licenseService;
        this.rateLimiterService = rateLimiterService;
        this.searchOperator = searchOperator;
    }

    public void registerApi(Jooby jooby) {
        jooby.get("/api/v1/", ctx -> {
            ctx.sendRedirect(StatusCode.TEMPORARY_REDIRECT, "https://about.marginalia-search.com/article/api/");
            return ctx;
        });

        jooby.errorCode(LicenseService.NoSuchKeyException.class, StatusCode.UNAUTHORIZED);

        jooby.get("/api/v1/{key}", this::keyInfo);
        jooby.get("/api/v1/{key}/", this::keyInfo);
        jooby.get("/api/v1/{key}/search/*", this::search);
    }


    private Object keyInfo(Context ctx) {
        Value key = ctx.path("key");
        if (key.isMissing()) {
            ctx.setResponseCode(400);
            return ctx;
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(key.value());
        } catch (LicenseService.NoSuchKeyException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return null;
        } catch (IOException e) {
            ctx.setResponseCode(StatusCode.SERVER_ERROR_CODE);
            return null;
        }

        ctx.setResponseType("application/json");
        return gson.toJson(license);
    }

    private Object search(Context ctx) {

        Value queryVal = ctx.path("*");
        if (!queryVal.isPresent()) {
            ctx.setResponseCode(400);
            return ctx;
        }
        String query = queryVal.value();

        Value keyPathVal = ctx.path("key");
        if (keyPathVal.isMissing()) {
            ctx.setResponseCode(400);
            return ctx;
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(keyPathVal.value());
        } catch (LicenseService.NoSuchKeyException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return null;
        } catch (IOException ex) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            return null;
        }

        if (!license.hasOption(ApiLicenseOptions.ALLOW_V1_API)) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            return "API key only valid for V2 API (this is the deprecated V1 API)";
        }

        ctx.setResponseType("application/json");

        // Check if we have a cached response
        var cachedResponse = responseCache.getResults(license, query, ctx.queryString());

        if (cachedResponse.isPresent()) {
            ApiMetrics.wmsa_api_cache_hit_count.labelValues(license.key()).inc();
            return gson.toJson(cachedResponse.get());
        }

        if (!rateLimiterService.isAllowedQPM(license)) {
            ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
            ctx.setResponseCode(503);
            return ctx;
        }

        // When no cached response, do the search and cache the result
        var result = doSearch(license, query, ctx);
        responseCache.putResults(license, query, ctx.queryString(), result);
        return gson.toJson(result);
    }

    private ApiSearchResults doSearch(ApiLicense license, String query, Context context) {
        int count = context.query().get("count").intValue(20);
        int domainCount = context.query().get("dc").intValue(2);
        int index = context.query().get("index").intValue(20);
        int nsfw = context.query().get("nsfw").intValue(1);
        int page = context.query().get("page").intValue(1);
        String langIsoCode = context.query("lang").value("en");

        NsfwFilterTier nsfwFilterTier;
        try {
            nsfwFilterTier = NsfwFilterTier.fromCodedValue(nsfw);
        }
        catch (IllegalArgumentException e) {
            context.setResponseCode(400);
            return null;
        }

        try (var _ = ApiMetrics.wmsa_api_query_time.labelValues(license.key()).startTimer())
        {
            return searchOperator
                    .v1query(query, count, domainCount, index, page, nsfwFilterTier, langIsoCode, license);
        }
        catch (TimeoutException ex) {
            context.setResponseCode(504);
            return null;
        }
    }
}
