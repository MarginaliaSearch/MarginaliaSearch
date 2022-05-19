package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor @Getter @ToString
public class EdgeSearchResults {
    public final Map<Integer, List<EdgeSearchResultItem>> results;

    public EdgeSearchResults() {
        results = new HashMap<>();
    }

    public int size() {
        return results.values().stream().mapToInt(List::size).sum();
    }

    public Stream<EdgeSearchResultItem> stream() {
        return results.values().stream().flatMap(List::stream);
    }

    public List<EdgeSearchResultItem> getAllItems() {
        return stream().collect(Collectors.toList());
    }
}
