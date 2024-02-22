package nu.marginalia.ranking.results;


import nu.marginalia.api.searchquery.model.results.SearchResultKeywordScore;

import java.util.List;

public record ResultKeywordSet(List<SearchResultKeywordScore> keywords) {

    public int length() {
        return keywords.size();
    }
    public boolean isEmpty() { return length() == 0; }
    public boolean hasNgram() {
        for (var word : keywords) {
            if (word.keyword.contains("_")) {
                return true;
            }
        }
        return false;
    }
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), keywords);
    }
}
