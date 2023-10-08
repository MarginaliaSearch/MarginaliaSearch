package nu.marginalia.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import nu.marginalia.search.query.model.UserSearchParameters;

import java.util.List;

@AllArgsConstructor @Getter @Builder
public class DecoratedSearchResults {
    private final UserSearchParameters params;
    private final List<String> problems;
    private final String evalResult;

    public final List<UrlDetails> results;

    private final String focusDomain;
    private final int focusDomainId;

    public String getQuery() {
        return params.humanQuery();
    }
    public String getProfile() {
        return params.profile().name;
    }
    public String getJs() {
        return params.jsSetting().value;
    }
}
