package nu.marginalia.api.searchquery.model.results;

import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.idx.DocumentMetadata;

import java.util.Objects;

public final class SearchResultKeywordScore {
    @Deprecated
    public final int subquery;
    public final long termId;
    public final String keyword;
    private final long encodedWordMetadata;
    private final long encodedDocMetadata;

    private final int htmlFeatures;

    public SearchResultKeywordScore(String keyword,
                                    long termId,
                                    long encodedWordMetadata,
                                    long encodedDocMetadata,
                                    int htmlFeatures) {
        this.termId = termId;
        this.subquery = -1; // FIXME, deprecated
        this.keyword = keyword;
        this.encodedWordMetadata = encodedWordMetadata;
        this.encodedDocMetadata = encodedDocMetadata;
        this.htmlFeatures = htmlFeatures;
    }

    public boolean hasTermFlag(WordFlags flag) {
        return WordMetadata.hasFlags(encodedWordMetadata, flag.asBit());
    }

    public int positionCount() {
        return Long.bitCount(positions());
    }

    public int subquery() {
        return subquery;
    }
    public long positions() {
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

    public int htmlFeatures() {
        return htmlFeatures;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SearchResultKeywordScore) obj;
        return this.subquery == that.subquery &&
                Objects.equals(this.keyword, that.keyword) &&
                this.encodedWordMetadata == that.encodedWordMetadata &&
                this.encodedDocMetadata == that.encodedDocMetadata;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subquery, keyword, encodedWordMetadata, encodedDocMetadata);
    }

    @Override
    public String toString() {
        return "SearchResultKeywordScore[" +
                "set=" + subquery + ", " +
                "keyword=" + keyword + ", " +
                "encodedWordMetadata=" + new WordMetadata(encodedWordMetadata) + ", " +
                "encodedDocMetadata=" + new DocumentMetadata(encodedDocMetadata) + ']';
    }

}
