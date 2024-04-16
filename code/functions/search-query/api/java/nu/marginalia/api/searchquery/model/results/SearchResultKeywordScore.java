package nu.marginalia.api.searchquery.model.results;

import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;

import java.util.Objects;

public final class SearchResultKeywordScore {
    public final long termId;
    public final String keyword;
    private final long encodedWordMetadata;

    public SearchResultKeywordScore(String keyword,
                                    long termId,
                                    long encodedWordMetadata) {
        this.termId = termId;
        this.keyword = keyword;
        this.encodedWordMetadata = encodedWordMetadata;
    }

    public boolean hasTermFlag(WordFlags flag) {
        return WordMetadata.hasFlags(encodedWordMetadata, flag.asBit());
    }


    public long positions() {
        return WordMetadata.decodePositions(encodedWordMetadata);
    }

    public boolean isKeywordSpecial() {
        return keyword.contains(":") || hasTermFlag(WordFlags.Synthetic);
    }

    public long encodedWordMetadata() {
        return encodedWordMetadata;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SearchResultKeywordScore) obj;
        return Objects.equals(this.termId, that.termId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(termId);
    }

    @Override
    public String toString() {
        return "SearchResultKeywordScore[" +
                "keyword=" + keyword + ", " +
                "encodedWordMetadata=" + new WordMetadata(encodedWordMetadata) + ']';
    }

}
