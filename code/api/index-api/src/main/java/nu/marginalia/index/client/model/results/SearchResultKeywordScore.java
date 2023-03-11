package nu.marginalia.index.client.model.results;

import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.crawl.EdgePageDocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;

import java.util.Objects;

public final class SearchResultKeywordScore {
    public final int subquery;
    public final String keyword;
    private final long encodedWordMetadata;
    private final long encodedDocMetadata;
    private final boolean hasPriorityTerms;

    public SearchResultKeywordScore(int subquery,
                                    String keyword,
                                    long encodedWordMetadata,
                                    long encodedDocMetadata,
                                    boolean hasPriorityTerms) {
        this.subquery = subquery;
        this.keyword = keyword;
        this.encodedWordMetadata = encodedWordMetadata;
        this.encodedDocMetadata = encodedDocMetadata;
        this.hasPriorityTerms = hasPriorityTerms;
    }

    private boolean hasTermFlag(EdgePageWordFlags flag) {
        return WordMetadata.hasFlags(encodedWordMetadata, flag.asBit());
    }

    public double documentValue() {
        long sum = 0;

        sum += DocumentMetadata.decodeQuality(encodedDocMetadata) / 5.;

        sum += DocumentMetadata.decodeTopology(encodedDocMetadata);

        if (DocumentMetadata.hasFlags(encodedDocMetadata, EdgePageDocumentFlags.Simple.asBit())) {
            sum += 20;
        }

        int rank = DocumentMetadata.decodeRank(encodedDocMetadata) - 13;
        if (rank < 0)
            sum += rank / 2;
        else
            sum += rank / 4;

        return sum;
    }

    public double termValue() {
        double sum = 0;

        if (hasTermFlag(EdgePageWordFlags.Title)) {
            sum -= 15;
        }

        if (hasTermFlag(EdgePageWordFlags.Site)) {
            sum -= 10;
        } else if (hasTermFlag(EdgePageWordFlags.SiteAdjacent)) {
            sum -= 5;
        }

        if (hasTermFlag(EdgePageWordFlags.Subjects)) {
            sum -= 10;
        }
        if (hasTermFlag(EdgePageWordFlags.NamesWords)) {
            sum -= 1;
        }

        if (hasTermFlag(EdgePageWordFlags.UrlDomain)) {
            sum -= 5;
        }

        if (hasTermFlag(EdgePageWordFlags.UrlPath)) {
            sum -= 5;
        }

        double tfIdf = WordMetadata.decodeTfidf(encodedWordMetadata);
        int positionBits = WordMetadata.decodePositions(encodedWordMetadata);

        sum -= tfIdf / 10.;
        sum -= Integer.bitCount(positionBits) / 3.;

        return sum;
    }

    public int subquery() {
        return subquery;
    }
    public int positions() {
        return WordMetadata.decodePositions(encodedWordMetadata);
    }

    public boolean isKeywordSpecial() {
        return keyword.contains(":") || hasTermFlag(EdgePageWordFlags.Synthetic);
    }

    public boolean isKeywordRegular() {
        return !keyword.contains(":")
                && !hasTermFlag(EdgePageWordFlags.Synthetic);
    }

    public long encodedWordMetadata() {
        return encodedWordMetadata;
    }

    public long encodedDocMetadata() {
        return encodedDocMetadata;
    }

    public boolean hasPriorityTerms() {
        return hasPriorityTerms;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SearchResultKeywordScore) obj;
        return this.subquery == that.subquery &&
                Objects.equals(this.keyword, that.keyword) &&
                this.encodedWordMetadata == that.encodedWordMetadata &&
                this.encodedDocMetadata == that.encodedDocMetadata &&
                this.hasPriorityTerms == that.hasPriorityTerms;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subquery, keyword, encodedWordMetadata, encodedDocMetadata, hasPriorityTerms);
    }

    @Override
    public String toString() {
        return "SearchResultKeywordScore[" +
                "set=" + subquery + ", " +
                "keyword=" + keyword + ", " +
                "encodedWordMetadata=" + encodedWordMetadata + ", " +
                "encodedDocMetadata=" + encodedDocMetadata + ", " +
                "hasPriorityTerms=" + hasPriorityTerms + ']';
    }

}
