package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record QueryResponse(SearchSpecification specs,
                            List<DecoratedSearchResultItem> results,
                            List<String> searchTermsHuman,
                            List<String> problems,
                            int currentPage,
                            int totalPages,
                            @Nullable String domain)
{
    public Set<String> getAllKeywords() {
        return new HashSet<>(specs.query.searchTermsInclude);
    }
}
