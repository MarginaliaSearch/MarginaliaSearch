package nu.marginalia.index.client.model.results;

import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.crawl.EdgePageDocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;

import static java.lang.Integer.lowestOneBit;
import static java.lang.Integer.numberOfTrailingZeros;

public record EdgeSearchResultKeywordScore(int set,
                                           String keyword,
                                           long encodedWordMetadata,
                                           long encodedDocMetadata,
                                           boolean hasPriorityTerms) {
    public double documentValue() {
        long sum = 0;

        sum += DocumentMetadata.decodeQuality(encodedDocMetadata) / 5.;

        sum += DocumentMetadata.decodeTopology(encodedDocMetadata);

        if (DocumentMetadata.hasFlags(encodedDocMetadata, EdgePageDocumentFlags.Simple.asBit())) {
            sum +=  20;
        }

        int rank = DocumentMetadata.decodeRank(encodedDocMetadata) - 13;
        if (rank < 0)
            sum += rank / 2;
        else
            sum += rank / 4;

        return sum;
    }

    private boolean hasTermFlag(EdgePageWordFlags flag) {
        return WordMetadata.hasFlags(encodedWordMetadata, flag.asBit());
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

        sum -= WordMetadata.decodeTfidf(encodedWordMetadata) / 50.;
        sum += firstPos() / 5.;
        sum -= Integer.bitCount(positions()) / 3.;

        return sum;
    }

    public int firstPos() {
        return numberOfTrailingZeros(lowestOneBit(WordMetadata.decodePositions(encodedWordMetadata)));
    }
    public int positions() { return WordMetadata.decodePositions(encodedWordMetadata); }
    public boolean isSpecial() { return keyword.contains(":") || hasTermFlag(EdgePageWordFlags.Synthetic); }
    public boolean isRegular() {
        return !keyword.contains(":")
            && !hasTermFlag(EdgePageWordFlags.Synthetic);
    }
}
