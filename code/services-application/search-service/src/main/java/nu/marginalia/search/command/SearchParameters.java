package nu.marginalia.search.command;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.model.SearchProfile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record SearchParameters(String query,
                               SearchProfile profile,
                               SearchJsParameter js,
                               SearchRecentParameter recent,
                               SearchAdtechParameter adtech
                               ) {
    public String profileStr() {
        return profile.filterId;
    }

    public SearchParameters withProfile(SearchProfile profile) {
        return new SearchParameters(query, profile, js, recent, adtech);
    }

    public SearchParameters withJs(SearchJsParameter js) {
        return new SearchParameters(query, profile, js, recent, adtech);
    }
    public SearchParameters withAdtech(SearchAdtechParameter adtech) {
        return new SearchParameters(query, profile, js, recent, adtech);
    }

    public SearchParameters withRecent(SearchRecentParameter recent) {
        return new SearchParameters(query, profile, js, recent, adtech);
    }

    public String renderUrl(WebsiteUrl baseUrl) {
        String path = String.format("/search?query=%s&profile=%s&js=%s&adtech=%s&recent=%s",
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                URLEncoder.encode(profile.filterId, StandardCharsets.UTF_8),
                URLEncoder.encode(js.value, StandardCharsets.UTF_8),
                URLEncoder.encode(adtech.value, StandardCharsets.UTF_8),
                URLEncoder.encode(recent.value, StandardCharsets.UTF_8)
                );

        return baseUrl.withPath(path);
    }
}
