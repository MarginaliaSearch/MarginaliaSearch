package nu.marginalia.wmsa.edge.model.search;

import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;

import static java.lang.Integer.lowestOneBit;
import static java.lang.Integer.numberOfTrailingZeros;

public record EdgeSearchResultKeywordScore(int set,
                                           String keyword,
                                           EdgePageWordMetadata metadata) {
    public double documentValue() {
        long sum = 0;
        sum += metadata.quality() / 5.;
        if (metadata.flags().contains(EdgePageWordFlags.Simple)) {
            sum +=  20;
        }
        return sum;
    }

    public double termValue() {
        double sum = 0;

        if (metadata.flags().contains(EdgePageWordFlags.Title)) {
            sum -=  15;
        }

        if (metadata.flags().contains(EdgePageWordFlags.Site)) {
            sum -= 10;
        }
        else if (metadata.flags().contains(EdgePageWordFlags.SiteAdjacent)) {
            sum -= 5;
        }

        if (metadata.flags().contains(EdgePageWordFlags.Subjects)) {
            sum -= 10;
        }
        if (metadata.flags().contains(EdgePageWordFlags.NamesWords)) {
            sum -= 1;
        }

        sum -= metadata.tfIdf() / 50.;
        sum += firstPos() / 5.;
        sum -= Integer.bitCount(positions()) / 3.;

        return sum;
    }

    public int firstPos() {
        return numberOfTrailingZeros(lowestOneBit(metadata.positions()));
    }
    public int positions() { return metadata.positions(); }
    public boolean isSpecial() { return keyword.contains(":"); }
    public boolean isRegular() { return !keyword.contains(":"); }
}
