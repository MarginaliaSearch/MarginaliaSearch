package nu.marginalia.wmsa.edge.model.search;

import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ToString @Getter @Builder @With @AllArgsConstructor
public class EdgeSearchSpecification {

    public List<Integer> buckets;
    public List<EdgeSearchSubquery> subqueries;
    public List<Integer> domains;

    public final int limitByDomain;
    public final int limitTotal;

    public final String humanQuery;

    public final int timeoutMs;
    public final int fetchSize;

    public final Integer quality;
    public final Integer rank;

    public List<String> allIncludeSearchTerms() {
        Set<String> searchTerms = new HashSet<>(64);
        for (var query : subqueries) {
            searchTerms.addAll(query.searchTermsInclude);
        }
        return new ArrayList<>(searchTerms);
    }
}
