package nu.marginalia.api.searchquery.model.query;

import java.util.List;

public record SearchCoherenceConstraint(boolean mandatory, List<String> terms) {
    public static SearchCoherenceConstraint mandatory(String... terms) {
        return new SearchCoherenceConstraint(true, List.of(terms));
    }
    public static SearchCoherenceConstraint mandatory(List<String> terms) {
        return new SearchCoherenceConstraint(true, List.copyOf(terms));
    }

    public static SearchCoherenceConstraint optional(String... terms) {
        return new SearchCoherenceConstraint(false, List.of(terms));
    }
    public static SearchCoherenceConstraint optional(List<String> terms) {
        return new SearchCoherenceConstraint(false, List.copyOf(terms));
    }

    public int size() {
        return terms.size();
    }
}
