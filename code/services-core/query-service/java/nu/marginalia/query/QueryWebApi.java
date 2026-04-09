package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;
import nu.marginalia.functions.searchquery.QueryGRPCService;
import nu.marginalia.index.api.IndexClient;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNullElse;

public class QueryWebApi {
    private final Gson gson = GsonFactory.get();
    private final QueryGRPCService queryGRPCService;
    private final HikariDataSource dataSource;
    private final MustacheRenderer<Object> searchRenderer;

    @Inject
    public QueryWebApi(QueryGRPCService queryGRPCService,
                       HikariDataSource dataSource,
                       RendererFactory rendererFactory) throws IOException
    {
        this.queryGRPCService = queryGRPCService;
        this.dataSource = dataSource;
        this.searchRenderer = rendererFactory.renderer("search");
    }

    public Object handleDomainInfo(Request request, Response response) throws SQLException {
        String domainName = request.splat()[0];
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT 
                    ID,
                    STATE,
                    NODE_AFFINITY
                FROM EC_DOMAIN
                WHERE DOMAIN_NAME = ?
                """))
        {
            stmt.setString(1, domainName);
            var rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> result = new HashMap<>();

                List<Map.Entry<String, Object>> entries = new ArrayList<>();

                Optional.ofNullable(rs.getObject("ID", Integer.class)).ifPresent(val -> result.put("ID", val));
                Optional.ofNullable(rs.getObject("STATE", String.class)).ifPresent(val -> result.put("STATE", val));
                Optional.ofNullable(rs.getObject("NODE_AFFINITY", String.class)).ifPresent(val -> result.put("NODE_AFFINITY", val));

                response.type("application/json");
                return gson.toJson(result);
            }
        }

        response.status(404);
        return "Unknown domain";
    }

    public Object handleDomainAvailability(Request request, Response response) throws SQLException {
        String domainName = request.splat()[0];
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT 
                    SERVER_AVAILABLE,
                    SERVER_IP,
                    SERVER_IP_ASN,
                    
                    HTTP_SCHEMA,
                    HTTP_ETAG,
                    HTTP_LAST_MODIFIED,
                    HTTP_STATUS,
                    HTTP_LOCATION,
                    HTTP_RESPONSE_TIME_MS,
                    
                    ERROR_CLASSIFICATION,
                    ERROR_MESSAGE,
                    
                    TS_LAST_PING,
                    TS_LAST_AVAILABLE,
                    TS_LAST_ERROR
                FROM DOMAIN_AVAILABILITY_INFORMATION
                INNER JOIN EC_DOMAIN
                ON EC_DOMAIN.ID = DOMAIN_AVAILABILITY_INFORMATION.DOMAIN_ID
                WHERE DOMAIN_NAME = ?
                """))
        {
            stmt.setString(1, domainName);
            var rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> result = new HashMap<>();

                List<Map.Entry<String, Object>> entries = new ArrayList<>();
                Optional.ofNullable(rs.getBoolean("SERVER_AVAILABLE")).ifPresent(val -> result.put("SERVER_AVAILABLE", val));
                Optional.ofNullable(rs.getBytes("SERVER_IP"))
                        .map(bytes -> IntStream.range(0, bytes.length).map(i -> (int) Byte.toUnsignedInt(bytes[i])).mapToObj(Integer::toString).collect(Collectors.joining(".")))
                        .ifPresent(val -> result.put("SERVER_IP", val));
                Optional.ofNullable(rs.getObject("SERVER_IP_ASN", Integer.class)).ifPresent(val -> result.put("SERVER_IP_ASN", val));
                Optional.ofNullable(rs.getObject("HTTP_SCHEMA", String.class)).ifPresent(val -> result.put("HTTP_SCHEMA", val));
                Optional.ofNullable(rs.getObject("HTTP_ETAG", String.class)).ifPresent(val -> result.put("HTTP_ETAG", val));
                Optional.ofNullable(rs.getObject("HTTP_LAST_MODIFIED", String.class)).ifPresent(val -> result.put("HTTP_LAST_MODIFIED", val));
                Optional.ofNullable(rs.getObject("HTTP_STATUS", Integer.class)).ifPresent(val -> result.put("HTTP_STATUS", val));
                Optional.ofNullable(rs.getObject("HTTP_LOCATION", String.class)).ifPresent(val -> result.put("HTTP_LOCATION", val));
                Optional.ofNullable(rs.getObject("HTTP_RESPONSE_TIME_MS", Integer.class)).ifPresent(val -> result.put("HTTP_RESPONSE_TIME_MS", val));
                Optional.ofNullable(rs.getTimestamp("TS_LAST_PING")).map(Timestamp::toInstant).ifPresent(val -> result.put("TS_LAST_PING", val));
                Optional.ofNullable(rs.getTimestamp("TS_LAST_AVAILABLE")).map(Timestamp::toInstant).ifPresent(val -> result.put("TS_LAST_AVAILABLE", val));
                Optional.ofNullable(rs.getTimestamp("TS_LAST_ERROR")).map(Timestamp::toInstant).ifPresent(val -> result.put("TS_LAST_ERROR", val));

                response.type("application/json");
                return gson.toJson(result);
            }
        }

        response.status(404);
        return "Unknown domain";
    }


    public Object handleApiSearch(Request request, Response response) {
        // Support both 'query' and 'q' parameter names
        String queryString = request.queryParams("query");
        if (queryString == null || queryString.isBlank()) {
            queryString = request.queryParams("q");
        }
        if (queryString == null || queryString.isBlank()) {
            return searchRenderer.render(new Object());
        }

        int count = clamp(intParam(request, "count", 20), 1, 100);
        int domainCount = clamp(intParam(request, "dc", 2), 1, 100);
        int timeout = clamp(intParam(request, "timeout", 150), 50, 250);
        int page = Math.max(1, intParam(request, "page", 1));
        String langIsoCode = requireNonNullElse(request.queryParams("lang"), "en");

        int nsfwValue = intParam(request, "nsfw", 1);
        NsfwFilterTier nsfwFilterTier;
        try {
            nsfwFilterTier = NsfwFilterTier.fromCodedValue(nsfwValue);
        }
        catch (IllegalArgumentException e) {
            response.status(400);
            return "Invalid 'nsfw' parameter value";
        }

        QueryFilterSpec filterSpec = resolveFilter(request.queryParams("filter"));

        IndexClient.Pagination pagination = new IndexClient.Pagination(page, count);

        QueryGRPCService.DetailedDirectResult result = queryGRPCService.executeApiQuery(
                queryString,
                RpcQueryLimits.newBuilder()
                        .setResultsByDomain(domainCount)
                        .setResultsTotal(Math.min(100, count))
                        .setTimeoutMs(timeout)
                        .build(),
                langIsoCode,
                nsfwFilterTier,
                filterSpec,
                pagination
        );

        int totalPages = 0;
        if (pagination.pageSize() > 0) {
            totalPages = (result.totalResults() + pagination.pageSize() - 1) / pagination.pageSize();
        }

        String accept = request.headers("Accept");
        if (accept != null && accept.contains("application/json")) {
            ApiSearchResults apiResults = new ApiSearchResults(
                    queryString,
                    page,
                    totalPages,
                    convertResults(result.result())
            );

            response.type("application/json");
            return gson.toJson(apiResults);
        }

        List<PaginationInfoPage> paginationInfo = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            paginationInfo.add(new PaginationInfoPage(i, i == page));
        }

        return searchRenderer.render(
                Map.of("query", queryString,
                        "pages", paginationInfo,
                        "results", result.result())
        );
    }

    private QueryFilterSpec resolveFilter(String filterName) {
        if (filterName == null || filterName.isBlank()) {
            return SearchFilterDefaults.NO_FILTER.asFilterSpec();
        }

        try {
            return SearchFilterDefaults.valueOf(filterName.toUpperCase()).asFilterSpec();
        }
        catch (IllegalArgumentException e) {
            return SearchFilterDefaults.NO_FILTER.asFilterSpec();
        }
    }

    private List<ApiSearchResult> convertResults(List<DecoratedSearchResultItem> items) {
        List<ApiSearchResult> results = new ArrayList<>(items.size());
        for (DecoratedSearchResultItem item : items) {
            results.add(convertResult(item));
        }
        return results;
    }

    private ApiSearchResult convertResult(DecoratedSearchResultItem item) {
        List<List<ApiSearchResultQueryDetails>> details = new ArrayList<>();

        if (item.rawIndexResult != null) {
            List<ApiSearchResultQueryDetails> keywordDetails = new ArrayList<>();
            for (SearchResultKeywordScore entry : item.rawIndexResult.keywordScores) {
                Set<String> flags = new LinkedHashSet<>();
                for (WordFlags flag : WordFlags.decode(entry.flags)) {
                    flags.add(flag.toString());
                }
                keywordDetails.add(new ApiSearchResultQueryDetails(entry.keyword, entry.positionCount, flags));
            }
            details.add(keywordDetails);
        }

        return new ApiSearchResult(
                item.url.toString(),
                item.getTitle(),
                item.getDescription(),
                sanitizeNaN(item.rankingScore, -100),
                item.getShortFormat(),
                item.resultsFromDomain,
                details
        );
    }

    private double sanitizeNaN(double value, double alternative) {
        if (!Double.isFinite(value)) {
            return alternative;
        }
        return value;
    }

    private int intParam(Request request, String name, int defaultValue) {
        String val = request.queryParams(name);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return parseInt(val);
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record ApiSearchResults(String query, int page, int pages, List<ApiSearchResult> results) {}

    record ApiSearchResult(String url,
                           String title,
                           String description,
                           double quality,
                           String format,
                           int resultsFromDomain,
                           List<List<ApiSearchResultQueryDetails>> details) {}

    record ApiSearchResultQueryDetails(String keyword, int count, Set<String> flagsUnstableAPI) {}

    record PaginationInfoPage(int number, boolean current) {}
}
