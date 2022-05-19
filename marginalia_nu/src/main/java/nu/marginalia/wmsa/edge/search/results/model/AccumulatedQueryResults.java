package nu.marginalia.wmsa.edge.search.results.model;

import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccumulatedQueryResults {

    private static final Logger logger = LoggerFactory.getLogger(AccumulatedQueryResults.class);

    public final Set<EdgeUrlDetails> results = new HashSet<>();

    public void add(EdgeUrlDetails details) {
        results.add(details);
    }

    public void append(int maxSize, List<TieredSearchResult> details) {
        for (var result : details) {

            if (size() >= maxSize) {
                break;
            }

            add(result.details);
        }
    }

    public int size() {
        return results.size();
    }

    public int count() {
        return results.size();
    }
}
