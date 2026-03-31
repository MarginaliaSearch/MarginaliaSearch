package nu.marginalia.query;

import com.google.gson.Gson;
import com.google.inject.Inject;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNullElse;

public class QueryWebApi {
    private final Gson gson = GsonFactory.get();
    private final QueryGRPCService queryGRPCService;
    private final MustacheRenderer<Object> searchRenderer;

    @Inject
    public QueryWebApi(QueryGRPCService queryGRPCService,
                       RendererFactory rendererFactory) throws IOException
    {
        this.queryGRPCService = queryGRPCService;
        this.searchRenderer = rendererFactory.renderer("search");
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
