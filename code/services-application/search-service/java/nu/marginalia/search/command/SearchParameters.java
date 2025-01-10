package nu.marginalia.search.command;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.search.model.SearchProfile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
                               int page,
                               int debug
                               ) {

    public static SearchParameters defaultsForQuery(WebsiteUrl url, String query, int page) {
        return new SearchParameters(
                url,
                query,
                SearchProfile.NO_FILTER,
                SearchJsParameter.DEFAULT,
                SearchRecentParameter.DEFAULT,
                SearchTitleParameter.DEFAULT,
                SearchAdtechParameter.DEFAULT,
                false,
                page,
                0);
    }

    public String profileStr() {
        return profile.filterId;
    }

    public SearchParameters withProfile(SearchProfile profile) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page, debug);
    }

    public SearchParameters withJs(SearchJsParameter js) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page, debug);
    }
    public SearchParameters withAdtech(SearchAdtechParameter adtech) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page, debug);
    }

    public SearchParameters withRecent(SearchRecentParameter recent) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, true, page, debug);
    }

    public SearchParameters withTitle(SearchTitleParameter title) {
        return new SearchParameters(url, query, profile, js, recent, title, adtech, true, page, debug);
    }

    public SearchParameters withPage(int page) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, false, page, debug);
    }

    public SearchParameters withQuery(String query) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, false, page, debug);
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
