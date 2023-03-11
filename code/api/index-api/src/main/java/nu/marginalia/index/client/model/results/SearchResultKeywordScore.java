package nu.marginalia.index.client.model.results;

import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.idx.DocumentFlags;
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

    private boolean hasTermFlag(WordFlags flag) {
        return WordMetadata.hasFlags(encodedWordMetadata, flag.asBit());
    }

    public double documentValue() {
        long sum = 0;

        sum += DocumentMetadata.decodeQuality(encodedDocMetadata) / 5.;

        sum += DocumentMetadata.decodeTopology(encodedDocMetadata);

        if (DocumentMetadata.hasFlags(encodedDocMetadata, DocumentFlags.Simple.asBit())) {
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

        double tfIdf = WordMetadata.decodeTfidf(encodedWordMetadata);
        int positionBits = WordMetadata.decodePositions(encodedWordMetadata);

        if (hasTermFlag(WordFlags.Title)) {
            sum -= 15;
        }

        if (hasTermFlag(WordFlags.Site) && positionBits != 0) {
            sum -= 10;
        } else if (hasTermFlag(WordFlags.SiteAdjacent) && positionBits != 0) {
            sum -= 5;
        }

        if (hasTermFlag(WordFlags.Subjects)) {
            sum -= 10;
        }
        if (hasTermFlag(WordFlags.NamesWords)) {
            sum -= 1;
        }

        if (hasTermFlag(WordFlags.UrlDomain)) {
            sum -= 5;
        }

        if (hasTermFlag(WordFlags.UrlPath)) {
            sum -= 5;
        }


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
        return keyword.contains(":") || hasTermFlag(WordFlags.Synthetic);
    }

    public boolean isKeywordRegular() {
        return !keyword.contains(":")
                && !hasTermFlag(WordFlags.Synthetic);
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
