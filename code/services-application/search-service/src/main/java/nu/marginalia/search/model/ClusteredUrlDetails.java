package nu.marginalia.search.model;

import lombok.Getter;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.idx.WordFlags;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/** A class to hold a list of UrlDetails, grouped by domain, where the first one is the main result
 * and the rest are additional results, for summary display. */
public class ClusteredUrlDetails implements Comparable<ClusteredUrlDetails> {

    /** Create a new ClusteredUrlDetails from a collection of UrlDetails,
     * with the best result as "first", and the others, in descending order
     * of quality as the "rest"...
     *
     * @param details A collection of UrlDetails, which must not be empty.
     */
    public ClusteredUrlDetails(Collection<UrlDetails> details) {
        var queue = new PriorityQueue<>(details);

        if (queue.isEmpty())
            throw new IllegalArgumentException("Empty list of details");

        this.first = queue.poll();

        if (queue.isEmpty()) {
            this.rest = Collections.emptyList();
        }
        else {
            double bestScore = first.termScore;
            double scoreLimit = Math.min(4.0, bestScore * 1.25);

            this.rest = queue
                    .stream()
                    .filter(this::isEligbleForInclusion)
                    .takeWhile(next -> next.termScore <= scoreLimit)
                    .toList();
        }

    }

    private boolean isEligbleForInclusion(UrlDetails urlDetails) {
        return urlDetails.resultItem.keywordScores.stream()
                .filter(score -> !score.keyword.contains(":"))
                .collect(Collectors.toMap(
                        score -> score.subquery,
                        score -> score.hasTermFlag(WordFlags.Title)
                               | score.hasTermFlag(WordFlags.ExternalLink)
                               | score.hasTermFlag(WordFlags.UrlDomain)
                               | score.hasTermFlag(WordFlags.UrlPath)
                               | score.hasTermFlag(WordFlags.Subjects)
                        ,
                        (a, b) -> a && b
                ))
                .containsValue(Boolean.TRUE);
    }

    public ClusteredUrlDetails(@NotNull UrlDetails onlyFirst) {
        this.first = onlyFirst;
        this.rest = Collections.emptyList();
    }

    @NotNull
    @Getter
    public final UrlDetails first;

    @NotNull
    @Getter
    public final List<UrlDetails> rest;

    public EdgeDomain getDomain() {
        return first.url.getDomain();
    }

    public boolean hasMultiple() {
        return !rest.isEmpty();
    }

    /** Returns the total number of results from the same domain,
     * including such results that are not included here. */
    public int totalCount() {
        return first.resultsFromSameDomain;
    }

    public int remainingCount() {
        return totalCount() - 1 - rest.size();
    }

    @Override
    public int compareTo(@NotNull ClusteredUrlDetails o) {
        return Objects.compare(first, o.first, UrlDetails::compareTo);
    }
}
