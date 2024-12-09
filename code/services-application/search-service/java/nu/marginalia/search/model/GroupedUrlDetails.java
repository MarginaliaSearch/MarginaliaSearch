package nu.marginalia.search.model;

import nu.marginalia.model.EdgeDomain;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** A number of url details grouped by their domain.  This is conceptually similar to
 * ClusteredUrlDetails, but it has more logic to conditionally perform this grouping operation,
 * whereas this class always groups the domains.
 *  */
public record GroupedUrlDetails (List<UrlDetails> urlDetails) {
    public GroupedUrlDetails(List<UrlDetails> urlDetails) {
        this.urlDetails = urlDetails;
        if (urlDetails.isEmpty()) {
            throw new IllegalArgumentException("urlDetails must never be empty");
        }
    }
    public EdgeDomain domain() {
        return urlDetails.getFirst().getUrl().domain;
    }

    public UrlDetails first() {
        return urlDetails.getFirst();
    }

    public static List<GroupedUrlDetails> groupResults(List<UrlDetails> details) {
        return details.stream()
                .sorted(Comparator.comparing(d -> d.termScore))
                .collect(Collectors.groupingBy(d -> d.getUrl().domain))
                .values().stream().map(GroupedUrlDetails::new)
                .toList();
    }
}
