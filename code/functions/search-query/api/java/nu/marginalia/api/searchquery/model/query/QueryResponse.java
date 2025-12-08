package nu.marginalia.api.searchquery.model.query;

import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcQueryTerms;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record QueryResponse(RpcQueryLimits limits,
                            RpcQueryTerms terms,
                            List<DecoratedSearchResultItem> results,
                            List<String> searchTermsHuman,
                            List<String> problems,
                            int currentPage,
                            int totalPages,
                            @Nullable String domain)
{
    public Set<String> getAllKeywords() {
        return new HashSet<>(terms.getTermsQueryList());
    }
}
