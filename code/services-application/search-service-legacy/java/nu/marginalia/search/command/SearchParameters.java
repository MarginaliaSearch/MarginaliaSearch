package nu.marginalia.search.command;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.search.model.SearchProfile;
import spark.Request;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static nu.marginalia.search.command.SearchRecentParameter.RECENT;

public record SearchParameters(String query,
                               SearchProfile profile,
                               SearchJsParameter js,
                               SearchRecentParameter recent,
                               SearchTitleParameter searchTitle,
                               SearchAdtechParameter adtech,
                               boolean newFilter,
                               int page
                               ) {

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

    public RpcTemporalBias.Bias temporalBias() {
        if (recent == RECENT) {
            return RpcTemporalBias.Bias.RECENT;
        }
        else if (profile == SearchProfile.VINTAGE) {
            return RpcTemporalBias.Bias.OLD;
        }

        return RpcTemporalBias.Bias.NONE;
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
