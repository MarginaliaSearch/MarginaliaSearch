package nu.marginalia.search.command;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.search.model.SearchProfile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static nu.marginalia.search.command.SearchRecentParameter.RECENT;

public record SearchParameters(String query,
                               SearchProfile profile,
                               SearchJsParameter js,
                               SearchRecentParameter recent,
                               SearchTitleParameter title,
                               SearchAdtechParameter adtech
                               ) {
    public String profileStr() {
        return profile.filterId;
    }

    public SearchParameters withProfile(SearchProfile profile) {
        return new SearchParameters(query, profile, js, recent, title, adtech);
    }

    public SearchParameters withJs(SearchJsParameter js) {
        return new SearchParameters(query, profile, js, recent, title, adtech);
    }
    public SearchParameters withAdtech(SearchAdtechParameter adtech) {
        return new SearchParameters(query, profile, js, recent, title, adtech);
    }

    public SearchParameters withRecent(SearchRecentParameter recent) {
        return new SearchParameters(query, profile, js, recent, title, adtech);
    }

    public SearchParameters withTitle(SearchTitleParameter title) {
        return new SearchParameters(query, profile, js, recent, title, adtech);
    }

    public String renderUrl(WebsiteUrl baseUrl) {
        String path = String.format("/search?query=%s&profile=%s&js=%s&adtech=%s&recent=%s&searchTitle=%s",
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                URLEncoder.encode(profile.filterId, StandardCharsets.UTF_8),
                URLEncoder.encode(js.value, StandardCharsets.UTF_8),
                URLEncoder.encode(adtech.value, StandardCharsets.UTF_8),
                URLEncoder.encode(recent.value, StandardCharsets.UTF_8),
                URLEncoder.encode(title.value, StandardCharsets.UTF_8)
                );

        return baseUrl.withPath(path);
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
        if (title == SearchTitleParameter.TITLE) {
            return QueryStrategy.REQUIRE_FIELD_TITLE;
        }

        return QueryStrategy.AUTO;
    }
}
