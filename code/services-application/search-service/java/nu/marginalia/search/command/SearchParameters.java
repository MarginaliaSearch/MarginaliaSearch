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
                               int page
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
                page);
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

        StringBuilder pathBuilder = new StringBuilder("/search?");
        pathBuilder.append("query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));

        if (profile != SearchProfile.NO_FILTER) {
            pathBuilder.append("&profile=").append(URLEncoder.encode(profile.filterId, StandardCharsets.UTF_8));
        }
        if (js != SearchJsParameter.DEFAULT) {
            pathBuilder.append("&js=").append(URLEncoder.encode(js.value, StandardCharsets.UTF_8));
        }
        if (adtech != SearchAdtechParameter.DEFAULT) {
            pathBuilder.append("&adtech=").append(URLEncoder.encode(adtech.value, StandardCharsets.UTF_8));
        }
        if (recent != SearchRecentParameter.DEFAULT) {
            pathBuilder.append("&recent=").append(URLEncoder.encode(recent.value, StandardCharsets.UTF_8));
        }
        if (searchTitle != SearchTitleParameter.DEFAULT) {
            pathBuilder.append("&searchTitle=").append(URLEncoder.encode(searchTitle.value, StandardCharsets.UTF_8));
        }
        if (page != 1) {
            pathBuilder.append("&page=").append(page);
        }
        if (newFilter) {
            pathBuilder.append("&newfilter=").append(Boolean.valueOf(newFilter).toString());
        }

        return pathBuilder.toString();
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
