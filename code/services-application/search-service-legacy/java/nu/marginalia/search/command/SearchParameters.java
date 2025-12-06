package nu.marginalia.search.command;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.search.model.SearchProfile;
import spark.Request;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record SearchParameters(String query,
                               SearchProfile profile,
                               SearchJsParameter js,
                               SearchRecentParameter recent,
                               SearchTitleParameter searchTitle,
                               SearchAdtechParameter adtech,
                               boolean newFilter,
                               int page
                               ) {

    public static SearchParameters defaultsForQuery(String query, int page) {
        return new SearchParameters(
                query,
                SearchProfile.NO_FILTER,
                SearchJsParameter.DEFAULT,
                SearchRecentParameter.DEFAULT,
                SearchTitleParameter.DEFAULT,
                SearchAdtechParameter.DEFAULT,
                false,
                page
        );
    }

    public NsfwFilterTier filterTier() {
        return NsfwFilterTier.DANGER;
    }

    public SearchParameters(String queryString, Request request) {
        this(
                queryString,
                SearchProfile.getSearchProfile(request.queryParams("profile")),
                SearchJsParameter.parse(request.queryParams("js")),
                SearchRecentParameter.parse(request.queryParams("recent")),
                SearchTitleParameter.parse(request.queryParams("searchTitle")),
                SearchAdtechParameter.parse(request.queryParams("adtech")),
                "true".equals(request.queryParams("newfilter")),
                Integer.parseInt(Objects.requireNonNullElse(request.queryParams("page"), "1"))
            );
    }

    public String profileStr() {
        return profile.filterId;
    }

    public SearchParameters withProfile(SearchProfile profile) {
        return new SearchParameters(query, profile, js, recent, searchTitle, adtech, true, page);
    }

    public SearchParameters withJs(SearchJsParameter js) {
        return new SearchParameters(query, profile, js, recent, searchTitle, adtech, true, page);
    }
    public SearchParameters withAdtech(SearchAdtechParameter adtech) {
        return new SearchParameters(query, profile, js, recent, searchTitle, adtech, true, page);
    }

    public SearchParameters withRecent(SearchRecentParameter recent) {
        return new SearchParameters(query, profile, js, recent, searchTitle, adtech, true, page);
    }

    public SearchParameters withTitle(SearchTitleParameter title) {
        return new SearchParameters(query, profile, js, recent, title, adtech, true, page);
    }

    public SearchParameters withPage(int page) {
        return new SearchParameters(query, profile, js, recent, searchTitle, adtech, false, page);
    }

    public String renderUrl(WebsiteUrl baseUrl) {
        String path = String.format("/search?query=%s&profile=%s&js=%s&adtech=%s&recent=%s&searchTitle=%s&newfilter=%s&page=%d",
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                URLEncoder.encode(profile.filterId, StandardCharsets.UTF_8),
                URLEncoder.encode(js.value, StandardCharsets.UTF_8),
                URLEncoder.encode(adtech.value, StandardCharsets.UTF_8),
                URLEncoder.encode(recent.value, StandardCharsets.UTF_8),
                URLEncoder.encode(searchTitle.value, StandardCharsets.UTF_8),
                Boolean.valueOf(newFilter).toString(),
                page
                );

        return baseUrl.withPath(path);
    }

    public QueryFilterSpec asFilterSpec() {
        var namedFilter = profile.defaultFilter.asFilterSpec();

        List<String> excludeTerms = new ArrayList<>();

        excludeTerms.addAll(List.of(js.implictExcludeSearchTerms));
        excludeTerms.addAll(List.of(adtech.implictExcludeSearchTerms));

        if (excludeTerms.isEmpty()
                && recent == SearchRecentParameter.DEFAULT
                && searchTitle == SearchTitleParameter.DEFAULT)
            return namedFilter;

        var adHocFilter = QueryFilterSpec.FilterAdHoc.builder()
                .termsExclude(excludeTerms)
                .temporalBias(switch (recent) {
                            case RECENT -> RpcTemporalBias.Bias.RECENT;
                            default -> RpcTemporalBias.Bias.NONE;
                        })
                .queryStrategy(switch (searchTitle) {
                    case TITLE -> QueryStrategy.REQUIRE_FIELD_TITLE;
                    default -> QueryStrategy.AUTO;
                })
                .build();

        return new QueryFilterSpec.CombinedFilter(namedFilter, adHocFilter);
    }
}
