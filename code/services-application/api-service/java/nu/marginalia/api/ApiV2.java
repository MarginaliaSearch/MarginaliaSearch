package nu.marginalia.api;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.jooby.*;
import io.jooby.value.Value;
import nu.marginalia.api.domains.DomainInfoClient;
import nu.marginalia.api.domains.RpcDomainInfoPingData;
import nu.marginalia.api.domains.RpcDomainInfoResponse;
import nu.marginalia.api.domains.RpcDomainInfoSecurityData;
import nu.marginalia.api.domains.model.SimilarDomain;
import nu.marginalia.api.model.*;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.svc.FiltersService;
import nu.marginalia.api.svc.LicenseService;
import nu.marginalia.api.svc.RateLimiterService;
import nu.marginalia.api.svc.ResponseCache;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.gson.GsonFactory;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ApiV2 implements Extension {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Marker apiUsageMarker = MarkerFactory.getMarker("APIUSAGE");

    private final Gson gson = GsonFactory.get();
    private final ResponseCache responseCache;
    private final LicenseService licenseService;
    private final RateLimiterService rateLimiterService;
    private final ApiSearchOperator searchOperator;
    private final QueryClient queryClient;
    private final FiltersService filtersService;
    private final DomainInfoClient domainInfoClient;
    private final DbDomainQueries dbDomainQueries;

    @Inject
    public ApiV2(ResponseCache responseCache,
                 LicenseService licenseService,
                 RateLimiterService rateLimiterService,
                 ApiSearchOperator searchOperator,
                 QueryClient queryClient,
                 FiltersService filtersService,
                 DomainInfoClient domainInfoClient,
                 DbDomainQueries dbDomainQueries)
    {
        this.responseCache = responseCache;
        this.licenseService = licenseService;
        this.rateLimiterService = rateLimiterService;
        this.searchOperator = searchOperator;
        this.queryClient = queryClient;
        this.filtersService = filtersService;
        this.domainInfoClient = domainInfoClient;
        this.dbDomainQueries = dbDomainQueries;
    }

    @Override
    public void install(Jooby jooby) {
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

        jooby.get("/api/v2/site/{domain}", this::siteInfo);
        jooby.get("/api/v2/site/{domain}/similar", (ctx) -> relatedDomains(ctx, Relation.Similar));
        jooby.get("/api/v2/site/{domain}/linking", (ctx) -> relatedDomains(ctx, Relation.Linked));
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
            return "Missing API-Key header";
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

        // Voluntary over-billing protection via request header
        int limitBillableRequestsHeader = ctx.header("API-Limit-Billable-Requests").intValue(-1);
        if (limitBillableRequestsHeader >= 0
            && license.hasOption(ApiLicenseOptions.ALLOW_QUERY_DAILY_OVERUSE)
            && !rateLimiterService.hasRemainingDailyLimit(license))
        {

            long billableApiUsage = rateLimiterService.estimatedTotalApiUseForPeriod(license);

            if (billableApiUsage + 1 >= limitBillableRequestsHeader) {
                DailyLimitState limitState = new DailyLimitState.OverLimitBlock();

                ctx.setResponseHeader("API-Remaining-Daily-Capacity", limitState.remaining());
                ctx.setResponseHeader("API-Event-Type", limitState.name());
                ctx.setResponseHeader("API-Billable-Requests", billableApiUsage);

                ctx.setResponseCode(429);

                return "API usage exceeds provided API-Limit-Billable-Requests header";
            }
        }

        ApiSearchResults results = null;
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

            try {
                results = doSearch(license, query, ctx);
                if (results == null)
                    return null;
            }
            catch (RuntimeException ex) {
                logger.error("Error execuing query {}", ex);

                ctx.setResponseCode(500);
                return "Query Execution Failed";
            }

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

        if (license.hasOption(ApiLicenseOptions.ALLOW_QUERY_DAILY_OVERUSE)) {
            long billableApiUsage = rateLimiterService.estimatedTotalApiUseForPeriod(license);
            ctx.setResponseHeader("API-Billable-Requests", billableApiUsage);
        }

        ctx.setResponseType("application/json");
        ctx.setResponseHeader("API-Remaining-Daily-Capacity", limitState.remaining());
        ctx.setResponseHeader("API-Event-Type", limitState.name());

        return gson.toJson(results);
    }

    @NotNull
    private Object siteInfo(Context ctx) {
        Value apiKeyVal = ctx.header("API-Key");
        if (apiKeyVal.isMissing()) {
            ctx.setResponseCode(400);
            return "Missing API-Key header";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(apiKeyVal.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        if (!rateLimiterService.isAllowedSiteInfoQPM(license)) {
            ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
            ctx.setResponseCode(429);
            return "Site Info QPM Limit Exceeded";
        }

        if (!rateLimiterService.isAllowedSiteInfoQPD(license)) {
            ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
            ctx.setResponseCode(429);
            return "Site Info Daily Limit Exceeded";
        }

        String domainName = ctx.path("domain").value();

        int domainId;
        try {
            domainId = dbDomainQueries.getDomainId(new EdgeDomain(domainName));
        } catch (NoSuchElementException ex) {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
            return "Domain not known";
        }

        RpcDomainInfoResponse rpcResponse;
        try {
            rpcResponse = domainInfoClient.domainInformation(domainId)
                    .get(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            logger.error("Failed to fetch domain info for {}", domainName, ex);
            ctx.setResponseCode(StatusCode.SERVER_ERROR_CODE);
            return "Failed to fetch domain information";
        }

        ApiSiteInfoPing ping = null;
        if (rpcResponse.hasPingData()) {
            RpcDomainInfoPingData pd = rpcResponse.getPingData();
            ping = new ApiSiteInfoPing(
                    pd.getServerAvailable(),
                    pd.getHttpSchema(),
                    pd.getResponseTimeMs(),
                    pd.getTsLast() > 0
                            ? Instant.ofEpochMilli(pd.getTsLast())
                                .atOffset(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            : null,
                    pd.getErrorDesc().isEmpty() ? null : pd.getErrorDesc()
            );
        }

        ApiSiteInfoSecurity security = null;
        if (rpcResponse.hasSecurityData()) {
            RpcDomainInfoSecurityData sd = rpcResponse.getSecurityData();
            security = new ApiSiteInfoSecurity(
                    sd.getSslProtocol().isEmpty() ? null : sd.getSslProtocol(),
                    sd.getHttpVersion().isEmpty() ? null : sd.getHttpVersion(),
                    sd.getHttpCompression(),
                    sd.getSslCertSubject().isEmpty() ? null : sd.getSslCertSubject()
            );
        }

        rateLimiterService.registerSuccessfulSiteInfoQuery(license);

        ApiSiteInfo result = new ApiSiteInfo(
                rpcResponse.getDomain(),
                rpcResponse.getState(),
                rpcResponse.getBlacklisted(),
                ping,
                security
        );

        ctx.setResponseType("application/json");
        return gson.toJson(result);
    }

    enum Relation {
        Linked,
        Similar
    };

    @NotNull
    private Object relatedDomains(Context ctx, Relation relation) {
        Value apiKeyVal = ctx.header("API-Key");
        if (apiKeyVal.isMissing()) {
            ctx.setResponseCode(400);
            return "Missing API-Key header";
        }

        ApiLicense license;
        try {
            license = licenseService.getLicense(apiKeyVal.value());
        } catch (LicenseService.NoSuchKeyException | IOException ex) {
            ctx.setResponseCode(StatusCode.UNAUTHORIZED);
            return "";
        }

        if (!rateLimiterService.isAllowedSiteInfoQPM(license)) {
            ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
            ctx.setResponseCode(429);
            return "Site Info QPM Limit Exceeded";
        }

        if (!rateLimiterService.isAllowedSiteInfoQPD(license)) {
            ApiMetrics.wmsa_api_timeout_count.labelValues(license.key()).inc();
            ctx.setResponseCode(429);
            return "Site Info Daily Limit Exceeded";
        }

        String domainName = ctx.path("domain").value();
        int count = Math.clamp(ctx.query("count").intValue(25), 1, 100);

        int domainId;
        try {
            domainId = dbDomainQueries.getDomainId(new EdgeDomain(domainName));
        } catch (NoSuchElementException ex) {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
            return "Domain not known";
        }

        List<ApiSimilarDomain> results;
        try {
            Future<List<SimilarDomain>> future = switch(relation) {
                case Similar -> domainInfoClient.similarDomains(domainId, count);
                case Linked -> domainInfoClient.linkedDomains(domainId, count);
            };

            List<SimilarDomain> domains = future.get(500, TimeUnit.MILLISECONDS);

            results = new ArrayList<>(domains.size());
            for (SimilarDomain sd : domains) {
                results.add(new ApiSimilarDomain(
                        sd.url().domain.toString(),
                        sd.relatedness(),
                        sd.rank(),
                        sd.linkType().name(),
                        sd.indexed(),
                        sd.active()
                ));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            logger.error("Failed to fetch related domains for {}", domainName, ex);
            ctx.setResponseCode(StatusCode.SERVER_ERROR_CODE);
            return "Failed to fetch related domains";
        }

        rateLimiterService.registerSuccessfulSiteInfoQuery(license);

        ctx.setResponseType("application/json");
        return gson.toJson(new ApiSimilarDomains(domainName, results));
    }

    private ApiSearchResults doSearch(ApiLicense license, String query, Context context) {
        int count = context.query().get("count").intValue(20);
        int domainCount = context.query().get("dc").intValue(2);
        int timeout = context.query().get("timeout").intValue(150);
        String filterName = context.query().get("filter").valueOrNull();
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

        final QueryFilterSpec filter;

        if (filterName == null)
            filter = SearchFilterDefaults.NO_FILTER.asFilterSpec();
        else
            filter = new QueryFilterSpec.FilterByName(license.key(), filterName);

        try (var _ = ApiMetrics.wmsa_api_query_time.labelValues(license.key()).startTimer())
        {
            return searchOperator
                    .v2query(query, count, timeout, domainCount, page, filter, nsfwFilterTier, langIsoCode, license);
        }
        catch (TimeoutException ex) {
            context.setResponseCode(504);
            return null;
        }
    }
}
