package nu.marginalia.search.query.model;

import lombok.AllArgsConstructor;
import nu.marginalia.index.client.model.query.EdgeSearchSpecification;

import java.util.*;

@AllArgsConstructor
public class SearchQuery {
    public final EdgeSearchSpecification specs;

    public final Set<String> problems = new TreeSet<>();
    public final List<String> searchTermsHuman;
    public String domain;

    public SearchQuery(EdgeSearchSpecification justSpecs) {
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
