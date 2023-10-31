package nu.marginalia.index.client.model.results;

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

    public static SearchResultSet combine(SearchResultSet l, SearchResultSet r) {
        List<DecoratedSearchResultItem> combinedItems = new ArrayList<>(l.size() + r.size());
        combinedItems.addAll(l.results);
        combinedItems.addAll(r.results);

        // TODO: Do we combine these correctly?
        combinedItems.sort(Comparator.comparing(item -> item.rankingScore));

        return new SearchResultSet(combinedItems);
    }
}
