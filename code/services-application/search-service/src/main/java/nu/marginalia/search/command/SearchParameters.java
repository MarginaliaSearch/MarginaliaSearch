package nu.marginalia.search.command;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.model.SearchProfile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record SearchParameters(String query, SearchProfile profile, SearchJsParameter js) {
    public String profileStr() {
        return profile.filterId;
    }

    public SearchParameters withProfile(SearchProfile profile) {
        return new SearchParameters(query, profile, js);
    }

    public SearchParameters withJs(SearchJsParameter js) {
        return new SearchParameters(query, profile, js);
    }

    public String renderUrl(WebsiteUrl baseUrl) {
        String path = String.format("/search?query=%s&profile=%s&js=%s",
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                URLEncoder.encode(profile.filterId, StandardCharsets.UTF_8),
                URLEncoder.encode(js.value, StandardCharsets.UTF_8));

        return baseUrl.withPath(path);
    }
}
