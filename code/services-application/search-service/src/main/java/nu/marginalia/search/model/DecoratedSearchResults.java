package nu.marginalia.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import nu.marginalia.search.command.SearchParameters;

import java.util.List;

@AllArgsConstructor @Getter @Builder
public class DecoratedSearchResults {
    private final SearchParameters params;
    private final List<String> problems;
    private final String evalResult;

    public final List<UrlDetails> results;

    private final String focusDomain;
    private final int focusDomainId;
    private final SearchFilters filters;
    public String getQuery() {
        return params.query();
    }
    public String getProfile() {
        return params.profile().name;
    }
    public String getJs() {
        return params.js().value;
    }
}
