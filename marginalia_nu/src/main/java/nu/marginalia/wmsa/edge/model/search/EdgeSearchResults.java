package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@AllArgsConstructor @Getter @ToString
public class EdgeSearchResults {
    public final List<EdgeSearchResultItem> results;

    public EdgeSearchResults() {
        results = new ArrayList<>();
    }

    public int size() {
        return results.size();
    }

    public Stream<EdgeSearchResultItem> stream() {
        return results.stream();
    }
}
