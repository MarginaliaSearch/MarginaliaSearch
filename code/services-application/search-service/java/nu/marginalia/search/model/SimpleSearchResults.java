package nu.marginalia.search.model;

import java.util.List;

public class SimpleSearchResults {
    public final List<UrlDetails> results;
    public final List<ResultsPage> resultPages;

    public SimpleSearchResults(List<UrlDetails> results, List<ResultsPage> resultPages) {
        this.results = results;
        this.resultPages = resultPages;
    }
}
