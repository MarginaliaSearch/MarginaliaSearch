package nu.marginalia.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import nu.marginalia.search.command.SearchParameters;

import java.util.List;

/** A class to hold details about the search results,
 * as used by the handlebars templating engine to render
 * the search results page.
 */
@AllArgsConstructor
@Getter
@Builder
public class DecoratedSearchResults {
    private final SearchParameters params;
    private final List<String> problems;
    private final String evalResult;

    public final List<ClusteredUrlDetails> results;

    private final String focusDomain;
    private final int focusDomainId;
    private final SearchFilters filters;

    // These are used by the search form, they look unused in the IDE but are used by the mustache template,
    // DO NOT REMOVE THEM
    public int getResultCount() { return results.size(); }
    public String getQuery() { return params.query(); }
    public String getProfile() { return params.profile().filterId; }
    public String getJs() { return params.js().value; }
    public String getAdtech() { return params.adtech().value; }
    public String getRecent() { return params.recent().value; }
    public String getSearchTitle() { return params.searchTitle().value; }
    public Boolean isNewFilter() { return params.newFilter(); }
}
