package nu.marginalia.search.model;

import java.util.List;

public class UnrankedSearchResults {
    public final List<UrlDetails> results;
    public final String cursor;

    public UnrankedSearchResults(List<UrlDetails> results, String cursor) {
        this.results = results;
        this.cursor = cursor;
    }
}
