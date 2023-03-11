package nu.marginalia.search.query.model;

import lombok.AllArgsConstructor;
import nu.marginalia.index.client.model.query.SearchSpecification;

import java.util.*;

@AllArgsConstructor
public class SearchQuery {
    public final SearchSpecification specs;

    public final Set<String> problems = new TreeSet<>();
    public final List<String> searchTermsHuman;
    public String domain;

    public SearchQuery(SearchSpecification justSpecs) {
        searchTermsHuman = new ArrayList<>();
        specs = justSpecs;
    }

    public Set<String> getAllKeywords() {
        Set<String> keywords = new HashSet<>(100);
        for (var sq : specs.subqueries) {
            keywords.addAll(sq.searchTermsInclude);
        }
        return keywords;
    }
}
