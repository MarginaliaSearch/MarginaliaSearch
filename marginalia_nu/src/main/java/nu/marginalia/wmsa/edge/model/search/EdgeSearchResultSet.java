package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.List;
import java.util.Map;

@AllArgsConstructor @Getter @ToString
public class EdgeSearchResultSet {
    public Map<IndexBlock, List<EdgeSearchResults>> resultsList;

    public int size() {
        return resultsList.values().stream().mapToInt(List::size).sum();
    }
}
