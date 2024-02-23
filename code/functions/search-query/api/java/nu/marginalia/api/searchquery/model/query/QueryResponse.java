package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;

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
