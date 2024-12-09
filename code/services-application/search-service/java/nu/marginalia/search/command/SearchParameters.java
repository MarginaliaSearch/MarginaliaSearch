package nu.marginalia.search.command;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.search.model.SearchProfile;
import spark.Request;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.StringJoiner;

import static nu.marginalia.search.command.SearchRecentParameter.RECENT;

public record SearchParameters(WebsiteUrl url,
                               String query,
                               SearchProfile profile,
                               SearchJsParameter js,
                               SearchRecentParameter recent,
                               SearchTitleParameter searchTitle,
                               SearchAdtechParameter adtech,
                               boolean newFilter,
                               int page
                               ) {

    public static SearchParameters defaultsForQuery(WebsiteUrl url, String query, int page) {
        return new SearchParameters(
                url,
                "test",
                SearchProfile.NO_FILTER,
                SearchJsParameter.DEFAULT,
                SearchRecentParameter.DEFAULT,
                SearchTitleParameter.DEFAULT,
                SearchAdtechParameter.DEFAULT,
                false,
                page);
    }
    public static SearchParameters forRequest(String queryString, WebsiteUrl url, Request request) {
        return new SearchParameters(
                url,
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
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page);
    }

    public SearchParameters withJs(SearchJsParameter js) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page);
    }
    public SearchParameters withAdtech(SearchAdtechParameter adtech) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page);
    }

    public SearchParameters withRecent(SearchRecentParameter recent) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page);
    }

    public SearchParameters withTitle(SearchTitleParameter title) {
        return new SearchParameters(url, query, profile, js, recent, title, adtech, true, page);
    }

    public SearchParameters withPage(int page) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, false, page);
    }

    public SearchParameters withQuery(String query) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, false, page);
    }

    public String renderUrlWithoutSiteFocus() {
        String[] parts = query.split("\\s+");
        StringJoiner newQuery = new StringJoiner(" ");
        for (var part : parts) {
            if (!part.startsWith("site:")) {
                newQuery.add(part);
            }
        }
        return withQuery(newQuery.toString()).renderUrl();
    }

    public String renderUrlWithSiteFocus(EdgeDomain domain) {
        return withQuery(query + " site:"+domain.toString()).renderUrl();
    }

    public String renderUrl() {
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

        return path;
    }

    public ResultRankingParameters.TemporalBias temporalBias() {
        if (recent == RECENT) {
            return ResultRankingParameters.TemporalBias.RECENT;
        }
        else if (profile == SearchProfile.VINTAGE) {
            return ResultRankingParameters.TemporalBias.OLD;
        }

        return ResultRankingParameters.TemporalBias.NONE;
    }

    public QueryStrategy strategy() {
        if (searchTitle == SearchTitleParameter.TITLE) {
            return QueryStrategy.REQUIRE_FIELD_TITLE;
        }

        return QueryStrategy.AUTO;
    }

    public SpecificationLimit yearLimit() {
        if (recent == RECENT)
            return SpecificationLimit.greaterThan(2018);

        return profile.getYearLimit();
    }
}
