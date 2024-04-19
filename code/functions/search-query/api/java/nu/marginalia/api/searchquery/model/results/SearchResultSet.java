package nu.marginalia.api.searchquery.model.results;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@AllArgsConstructor @Getter @ToString
public class SearchResultSet {
    public SearchResultSet() {
        results = new ArrayList<>();
    }

    public List<DecoratedSearchResultItem> results;
    public int size() {
        return results.size();
    }

}
