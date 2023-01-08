package nu.marginalia.wmsa.edge.model.search;

import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordMetadata;

import static java.lang.Integer.lowestOneBit;
import static java.lang.Integer.numberOfTrailingZeros;

public record EdgeSearchResultKeywordScore(int set,
                                           String keyword,
                                           long encodedWordMetadata,
                                           long encodedDocMetadata,
                                           boolean hasPriorityTerms) {
    public double documentValue() {
        long sum = 0;

        sum += EdgePageDocumentsMetadata.decodeQuality(encodedDocMetadata) / 5.;

        sum += EdgePageDocumentsMetadata.decodeTopology(encodedDocMetadata);

        if (EdgePageDocumentsMetadata.hasFlags(encodedDocMetadata, EdgePageDocumentFlags.Simple.asBit())) {
            sum +=  20;
        }


        return sum;
    }

    private boolean hasTermFlag(EdgePageWordFlags flag) {
        return EdgePageWordMetadata.hasFlags(encodedWordMetadata, flag.asBit());
    }

    public double termValue() {
        double sum = 0;

        if (hasTermFlag(EdgePageWordFlags.Title)) {
            sum -=  15;
        }

        if (hasTermFlag(EdgePageWordFlags.Site)) {
            sum -= 10;
        }
        else if (hasTermFlag(EdgePageWordFlags.SiteAdjacent)) {
            sum -= 5;
        }

        if (hasTermFlag(EdgePageWordFlags.Subjects)) {
            sum -= 10;
        }
        if (hasTermFlag(EdgePageWordFlags.NamesWords)) {
            sum -= 1;
        }

        sum -= EdgePageWordMetadata.decodeTfidf(encodedWordMetadata) / 50.;
        sum += firstPos() / 5.;
        sum -= Integer.bitCount(positions()) / 3.;

        return sum;
    }

    public int firstPos() {
        return numberOfTrailingZeros(lowestOneBit(EdgePageWordMetadata.decodePositions(encodedWordMetadata)));
    }
    public int positions() { return EdgePageWordMetadata.decodePositions(encodedWordMetadata); }
    public boolean isSpecial() { return keyword.contains(":") || hasTermFlag(EdgePageWordFlags.Synthetic); }
    public boolean isRegular() {
        return !keyword.contains(":")
            && !hasTermFlag(EdgePageWordFlags.Synthetic);
    }
}
