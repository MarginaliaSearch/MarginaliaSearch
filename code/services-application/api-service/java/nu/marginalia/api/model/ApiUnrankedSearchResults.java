package nu.marginalia.api.model;

import java.util.List;

public class ApiUnrankedSearchResults {
    private final String license;

    private String cursorNext;

    private final String query;
    private final List<ApiSearchResult> results;

    public ApiUnrankedSearchResults(String license,
                                    String query,
                                    String cursorNext,
                                    List<ApiSearchResult> results) {
        this.license = license;
        this.query = query;
        this.results = results;
    }

    public String getLicense() {
        return this.license;
    }

    public String getQuery() {
        return this.query;
    }

    public String getCursorNext() { return this.cursorNext; }

    public List<ApiSearchResult> getResults() {
        return this.results;
    }

    public ApiUnrankedSearchResults withLicense(String license) {
        return this.license == license ? this : new ApiUnrankedSearchResults(license, this.query, this.cursorNext, this.results);
    }

    public ApiUnrankedSearchResults withQuery(String query) {
        return this.query == query ? this : new ApiUnrankedSearchResults(this.license, query, this.cursorNext, this.results);
    }

    public ApiUnrankedSearchResults withResults(List<ApiSearchResult> results) {
        return this.results == results ? this : new ApiUnrankedSearchResults(this.license, this.query, this.cursorNext, results);
    }
}
