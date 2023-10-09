package nu.marginalia.query.model;

import nu.marginalia.index.client.model.query.SearchSpecification;
import nu.marginalia.index.client.model.results.DecoratedSearchResultItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record QueryResponse(SearchSpecification specs,
                            List<DecoratedSearchResultItem> results,
                            List<String> searchTermsHuman,
                            List<String> problems,
                            String domain)
{
    public Set<String> getAllKeywords() {
        Set<String> keywords = new HashSet<>(100);
        for (var sq : specs.subqueries) {
            keywords.addAll(sq.searchTermsInclude);
        }
        return keywords;
    }
}
