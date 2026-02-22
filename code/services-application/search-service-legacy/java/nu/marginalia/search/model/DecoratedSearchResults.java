package nu.marginalia.search.model;

import nu.marginalia.search.command.SearchParameters;

import java.util.List;

/**
 * A class to hold details about the search results,
 * as used by the handlebars templating engine to render
 * the search results page.
 */
public class DecoratedSearchResults {
    private final SearchParameters params;
    private final List<String> problems;
    private final String evalResult;

    public DecoratedSearchResults(SearchParameters params,
                                  List<String> problems,
                                  String evalResult,
                                  List<ClusteredUrlDetails> results,
                                  String focusDomain,
                                  int focusDomainId,
                                  SearchFilters filters,
                                  List<Page> resultPages) {
        this.params = params;
        this.problems = problems;
        this.evalResult = evalResult;
        this.results = results;
        this.focusDomain = focusDomain;
        this.focusDomainId = focusDomainId;
        this.filters = filters;
        this.resultPages = resultPages;
    }

    public final List<ClusteredUrlDetails> results;

    public static DecoratedSearchResultsBuilder builder() {
        return new DecoratedSearchResultsBuilder();
    }

    public SearchParameters getParams() {
        return params;
    }

    public List<String> getProblems() {
        return problems;
    }

    public String getEvalResult() {
        return evalResult;
    }

    public List<ClusteredUrlDetails> getResults() {
        return results;
    }

    public String getFocusDomain() {
        return focusDomain;
    }

    public int getFocusDomainId() {
        return focusDomainId;
    }

    public SearchFilters getFilters() {
        return filters;
    }

    public List<Page> getResultPages() {
        return resultPages;
    }

    private final String focusDomain;
    private final int focusDomainId;
    private final SearchFilters filters;

    private final List<Page> resultPages;

    public boolean isMultipage() {
        return resultPages.size() > 1;
    }

    public record Page(int number, boolean current, String href) {
    }

    // These are used by the search form, they look unused in the IDE but are used by the mustache template,
    // DO NOT REMOVE THEM
    public int getResultCount() {
        return results.size();
    }

    public String getQuery() {
        return params.query();
    }

    public String getProfile() {
        return params.profile().filterId;
    }

    public String getJs() {
        return params.js().value;
    }

    public String getAdtech() {
        return params.adtech().value;
    }

    public String getRecent() {
        return params.recent().value;
    }

    public String getSearchTitle() {
        return params.searchTitle().value;
    }

    public String getSst() { return params.sst(); }

    public int page() {
        return params.page();
    }

    public Boolean isNewFilter() {
        return params.newFilter();
    }


    public static class DecoratedSearchResultsBuilder {
        private SearchParameters params;
        private List<String> problems;
        private String evalResult;
        private List<ClusteredUrlDetails> results;
        private String focusDomain;
        private int focusDomainId;
        private SearchFilters filters;
        private List<Page> resultPages;

        DecoratedSearchResultsBuilder() {
        }

        public DecoratedSearchResultsBuilder params(SearchParameters params) {
            this.params = params;
            return this;
        }

        public DecoratedSearchResultsBuilder problems(List<String> problems) {
            this.problems = problems;
            return this;
        }

        public DecoratedSearchResultsBuilder evalResult(String evalResult) {
            this.evalResult = evalResult;
            return this;
        }

        public DecoratedSearchResultsBuilder results(List<ClusteredUrlDetails> results) {
            this.results = results;
            return this;
        }

        public DecoratedSearchResultsBuilder focusDomain(String focusDomain) {
            this.focusDomain = focusDomain;
            return this;
        }

        public DecoratedSearchResultsBuilder focusDomainId(int focusDomainId) {
            this.focusDomainId = focusDomainId;
            return this;
        }

        public DecoratedSearchResultsBuilder filters(SearchFilters filters) {
            this.filters = filters;
            return this;
        }

        public DecoratedSearchResultsBuilder resultPages(List<Page> resultPages) {
            this.resultPages = resultPages;
            return this;
        }

        public DecoratedSearchResults build() {
            return new DecoratedSearchResults(this.params, this.problems, this.evalResult, this.results, this.focusDomain, this.focusDomainId, this.filters, this.resultPages);
        }

        public String toString() {
            return "DecoratedSearchResults.DecoratedSearchResultsBuilder(params=" + this.params + ", problems=" + this.problems + ", evalResult=" + this.evalResult + ", results=" + this.results + ", focusDomain=" + this.focusDomain + ", focusDomainId=" + this.focusDomainId + ", filters=" + this.filters + ", resultPages=" + this.resultPages + ")";
        }
    }
}
