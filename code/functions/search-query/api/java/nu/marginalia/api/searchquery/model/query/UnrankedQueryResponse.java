package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;

import java.util.List;

public record UnrankedQueryResponse(List<DecoratedSearchResultItem> results, String encodedCursor) {
    public boolean hasMore() {
        return !"FIN".equals(encodedCursor);
    }
}
