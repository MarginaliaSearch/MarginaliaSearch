package nu.marginalia.api.model;

import java.util.List;

public class ApiSearchResults {
    private final String license;

    private final String query;
    private final List<ApiSearchResult> results;

    public ApiSearchResults(String license, String query, List<ApiSearchResult> results) {
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

    public List<ApiSearchResult> getResults() {
        return this.results;
    }

    public ApiSearchResults withLicense(String license) {
        return this.license == license ? this : new ApiSearchResults(license, this.query, this.results);
    }

    public ApiSearchResults withQuery(String query) {
        return this.query == query ? this : new ApiSearchResults(this.license, query, this.results);
    }

    public ApiSearchResults withResults(List<ApiSearchResult> results) {
        return this.results == results ? this : new ApiSearchResults(this.license, this.query, results);
    }
}
