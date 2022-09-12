package nu.marginalia.wmsa.edge.search.query.model;

import lombok.AllArgsConstructor;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;

import java.util.*;

@AllArgsConstructor
public class EdgeSearchQuery {
    public final EdgeSearchSpecification specs;

    public final Set<String> problems = new TreeSet<>();
    public final List<String> searchTermsHuman;
    public String domain;

    public EdgeSearchQuery(EdgeSearchSpecification justSpecs) {
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
