package nu.marginalia.ranking;

import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

public record ResultKeywordSet(SearchResultKeywordScore[] keywords) implements Iterable<SearchResultKeywordScore> {
    @NotNull
    @Override
    public Iterator<SearchResultKeywordScore> iterator() {
        return Arrays.stream(keywords).iterator();
    }

    public int length() {
        return keywords.length;
    }

    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), Arrays.toString(keywords));
    }
}
