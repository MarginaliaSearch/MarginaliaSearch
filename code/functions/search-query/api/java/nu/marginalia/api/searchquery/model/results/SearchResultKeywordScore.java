package nu.marginalia.api.searchquery.model.results;

import nu.marginalia.model.idx.WordFlags;

import java.util.Objects;

public final class SearchResultKeywordScore {
    public final long termId;
    public final String keyword;
    public byte flags;
    public int positionCount;

    public SearchResultKeywordScore(String keyword,
                                    long termId,
                                    byte flags,
                                    int positionCount) {
        this.termId = termId;
        this.keyword = keyword;
    }

    public boolean hasTermFlag(WordFlags flag) {
        return (flags & flag.asBit()) != 0;
    }


    public boolean isKeywordSpecial() {
        return keyword.contains(":") || hasTermFlag(WordFlags.Synthetic);
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
                "keyword=" + keyword + ']';
    }

}
