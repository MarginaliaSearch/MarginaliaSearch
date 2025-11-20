package nu.marginalia.search.model;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.searchquery.QueryFilterSpec;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.model.EdgeDomain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import static nu.marginalia.search.model.SearchRecentParameter.RECENT;

public record SearchParameters(WebsiteUrl url,
                               String query,
                               SearchProfile profile,
                               SearchJsParameter js,
                               SearchRecentParameter recent,
                               SearchTitleParameter searchTitle,
                               SearchAdtechParameter adtech,
                               String languageIsoCode,
                               boolean newFilter,
                               int page
                               ) {

    public NsfwFilterTier filterTier() {
        return NsfwFilterTier.DANGER;
    }

    public static SearchParameters defaultsForQuery(WebsiteUrl url, String query, int page) {
        return new SearchParameters(
                url,
                query,
                SearchProfile.NO_FILTER,
                SearchJsParameter.DEFAULT,
                SearchRecentParameter.DEFAULT,
                SearchTitleParameter.DEFAULT,
                SearchAdtechParameter.DEFAULT,
                "en",
                false,
                page);
    }

    public String profileStr() {
        return profile.filterId;
    }

    public SearchParameters withProfile(SearchProfile profile) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, languageIsoCode, true, page);
    }

    public SearchParameters withJs(SearchJsParameter js) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, languageIsoCode, true, page);
    }
    public SearchParameters withAdtech(SearchAdtechParameter adtech) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, languageIsoCode, true, page);
    }

    public SearchParameters withRecent(SearchRecentParameter recent) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, languageIsoCode, true, page);
    }

    public SearchParameters withTitle(SearchTitleParameter title) {
        return new SearchParameters(url, query, profile, js, recent, title, adtech, languageIsoCode, true, page);
    }

    public SearchParameters withLanguage(String languageIsoCode) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, languageIsoCode, true, page);
    }

    public SearchParameters withPage(int page) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, languageIsoCode, false, page);
    }

    public SearchParameters withQuery(String query) {
        return new SearchParameters(url, query, profile, js, recent, searchTitle, adtech, languageIsoCode, false, page);
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

    public String toggleSiteWildcard() {
        String[] parts = query.split("\\s+");
        StringJoiner newQuery = new StringJoiner(" ");
        for (var part : parts) {
            if (!part.startsWith("site:")) {
                newQuery.add(part);
            }
            else if (part.startsWith("site:*.")) {
                String domain = part.substring("site:*.".length());
                newQuery.add("site:" + domain);
            }
            else { // starts with "site:"
                var domain = new EdgeDomain(part.substring("site:".length()));
                newQuery.add("site:*." + domain.topDomain);
            }
        }
        return withQuery(newQuery.toString()).renderUrl();
    }

    public String renderUrlWithSiteFocus(EdgeDomain domain) {
        return withQuery(query + " site:"+domain.toString()).renderUrl();
    }

    public String renderUrl() {

        StringBuilder pathBuilder = new StringBuilder("/search?");

        if (query != null) {
            pathBuilder.append("query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        }
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
        if (!Objects.equals(languageIsoCode, "en")) {
            pathBuilder.append("&lang=").append(languageIsoCode);
        }
        if (newFilter) {
            pathBuilder.append("&newfilter=").append(Boolean.valueOf(newFilter).toString());
        }

        return pathBuilder.toString();
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
