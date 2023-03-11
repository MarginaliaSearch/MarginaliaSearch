package nu.marginalia.index.client.model.results;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@AllArgsConstructor @Getter @ToString
public class SearchResultSet {
    public List<SearchResultItem> results;

    public int size() {
        return results.size();
    }
}
