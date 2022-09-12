package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@AllArgsConstructor @Getter @ToString
public class EdgeSearchResultSet {
    public List<EdgeSearchResultItem> results;

    public int size() {
        return results.size();
    }
}
