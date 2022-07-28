package nu.marginalia.wmsa.edge.search.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;

import java.util.List;

@AllArgsConstructor @Getter
public class DecoratedSearchResults {
    private final EdgeUserSearchParameters params;
    private final List<String> problems;
    private final String evalResult;
    private final WikiArticles wiki;
    private final List<EdgeUrlDetails> results;

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
