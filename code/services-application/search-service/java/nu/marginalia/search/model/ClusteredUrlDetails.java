package nu.marginalia.search.model;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.WordFlags;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** A class to hold a list of UrlDetails, grouped by domain, where the first one is the main result
 * and the rest are additional results, for summary display. */
public class ClusteredUrlDetails implements Comparable<ClusteredUrlDetails> {

    @NotNull
    public final UrlDetails first;

    @NotNull
    public final List<UrlDetails> rest;

    /** Selects color scheme in the GUI for the result */
    public final PostColorScheme colorScheme;

    /** Create a new ClusteredUrlDetails from a collection of UrlDetails,
     * with the best result as "first", and the others, in descending order
     * of quality as the "rest"...
     *
     * @param details A collection of UrlDetails, which must not be empty.
     */
    public ClusteredUrlDetails(Collection<UrlDetails> details) {
        var items = new ArrayList<>(details);

        items.sort(Comparator.naturalOrder());

        if (items.isEmpty())
            throw new IllegalArgumentException("Empty list of details");

        this.first = items.removeFirst();
        this.rest = items;
        this.colorScheme = PostColorScheme.select(first);

        double bestScore = first.termScore;
        double scoreLimit = Math.min(4.0, bestScore * 1.25);

        this.rest.removeIf(urlDetail -> {
            if (urlDetail.termScore > scoreLimit)
                return false;

            for (var keywordScore : urlDetail.resultItem.keywordScores) {
                if (keywordScore.isKeywordSpecial())
                    continue;
                if (keywordScore.hasTermFlag(WordFlags.Title))
                    return false;
                if (keywordScore.hasTermFlag(WordFlags.ExternalLink))
                    return false;
                if (keywordScore.hasTermFlag(WordFlags.UrlDomain))
                    return false;
                if (keywordScore.hasTermFlag(WordFlags.UrlPath))
                    return false;
                if (keywordScore.hasTermFlag(WordFlags.Subjects))
                    return false;
            }

            return true;
        });

    }


    public ClusteredUrlDetails(@NotNull UrlDetails onlyFirst) {
        this.first = onlyFirst;
        this.rest = Collections.emptyList();
        this.colorScheme = PostColorScheme.select(first);
    }

    /** For tests */
    public ClusteredUrlDetails(@NotNull UrlDetails onlyFirst, @NotNull List<UrlDetails> rest) {
        this.first = onlyFirst;
        this.rest = rest;
        this.colorScheme = PostColorScheme.select(first);
    }

    // For renderer use, do not remove
    public @NotNull UrlDetails getFirst() {
        return first;
    }

    // For renderer use, do not remove
    public @NotNull List<UrlDetails> getRest() {
        return rest;
    }


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


    public enum PostColorScheme {
        // Hack: ensure these classes are also in jte/part/warmup.jte!

        Slate( "text-blue-800 dark:text-blue-200", "bg-blue-50 dark:bg-blue-950"),
        Green( "text-green-800 dark:text-green-200", "bg-green-50 dark:bg-green-950"),
        Purple("text-purple-800 dark:text-purple-200", "bg-purple-50 dark:bg-purple-950"),
        White("text-blue-950 dark:text-blue-50", "bg-gray-100 dark:bg-gray-900");

        PostColorScheme(String textColor, String backgroundColor) {
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
        }


        public static PostColorScheme select(UrlDetails result) {
            long encodedMetadata = result.resultItem.encodedDocMetadata;
            if (DocumentFlags.PlainText.isPresent(encodedMetadata)) {
                return Slate;
            }
            else if (DocumentFlags.GeneratorWiki.isPresent(encodedMetadata)) {
                return Green;
            }
            else if (DocumentFlags.GeneratorForum.isPresent(encodedMetadata)) {
                return Purple;
            }
            else {
                return White;
            }
        }

        public final String backgroundColor;
        public final String textColor;
    }
}
