package nu.marginalia.search;

import nu.marginalia.query.model.QueryResponse;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.ClusteredUrlDetails;
import nu.marginalia.search.model.UrlDetails;

import java.util.List;
import java.util.stream.Collectors;

/** Functions for clustering search results */
public class SearchResultClusterer {
    private SearchResultClusterer() {}

    public interface SearchResultClusterStrategy {
        List<ClusteredUrlDetails> clusterResults(List<UrlDetails> results, int total);
    }

    public static SearchResultClusterStrategy selectStrategy(QueryResponse response, SearchParameters params) {
        if (response.domain() != null && !response.domain().isBlank())
            return SearchResultClusterer::noOp;

        if (params.profile().clusterResults())
            return SearchResultClusterer::byDomain;

        return SearchResultClusterer::clusterThenSplit;
    }

    /** No clustering, just return the results as is */
    private static List<ClusteredUrlDetails> noOp(List<UrlDetails> results, int total) {
        if (results.isEmpty())
            return List.of();

        return results.stream()
                .map(ClusteredUrlDetails::new)
                .toList();
    }

    /** Cluster the results by domain, and return the top "total" clusters
     * sorted by the relevance of the best result
     */
    private static List<ClusteredUrlDetails> byDomain(List<UrlDetails> results, int total) {
        if (results.isEmpty())
            return List.of();

        return results.stream()
                .collect(
                        Collectors.groupingBy(details -> details.domainId)
                )
                .values().stream()
                .map(ClusteredUrlDetails::new)
                .mapMulti(ClusteredUrlDetails::splitSmallClusters) // split small clusters into singletons
                .sorted()
                .limit(total)
                .toList();
    }

    /** Cluster the results by domain to find the best result for each domain,
     * then split the clusters into singletons, and return the top "total" clusters
     */
    private static List<ClusteredUrlDetails> clusterThenSplit(List<UrlDetails> results, int total) {
        if (results.isEmpty())
            return List.of();

        return results.stream()
                .collect(
                        Collectors.groupingBy(details -> details.domainId)
                )
                .values().stream()
                .map(ClusteredUrlDetails::new)
                .mapMulti(ClusteredUrlDetails::forEachSingle)
                .sorted()
                .limit(total)
                .toList();
    }

}
