package nu.marginalia.search.model;

import nu.marginalia.WebsiteUrl;

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

    private final String languageIsoCode;

    public DecoratedSearchResults(SearchParameters params,
                                  List<String> problems,
                                  String evalResult,
                                  String languageIsoCode,
                                  List<ClusteredUrlDetails> results,
                                  String focusDomain,
                                  int focusDomainId,
                                  SearchFilters filters,
                                  List<ResultsPage> resultPages) {
        this.params = params;
        this.problems = problems;
        this.evalResult = evalResult;
        this.languageIsoCode = languageIsoCode;
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

    public String getLanguageIsoCode() {
        return languageIsoCode;
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

    public boolean hasFocusDomain() {
        return focusDomainId >= 0;
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    public SearchFilters getFilters() {
        return filters;
    }

    public List<ResultsPage> getResultPages() {
        return resultPages;
    }

    private final String focusDomain;
    private final int focusDomainId;

    private final SearchFilters filters;

    private final List<ResultsPage> resultPages;

    public boolean isMultipage() {
        return resultPages.size() > 1;
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
        private List<ResultsPage> resultPages;
        private String languageIsoCode;
        private WebsiteUrl websiteUrl;

        DecoratedSearchResultsBuilder() {
        }


        public DecoratedSearchResultsBuilder params(SearchParameters params) {
            this.params = params;
            return this;
        }

        public DecoratedSearchResultsBuilder languageIsoCode(String languageIsoCode) {
            this.languageIsoCode = languageIsoCode;
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

        public DecoratedSearchResultsBuilder resultPages(List<ResultsPage> resultPages) {
            this.resultPages = resultPages;
            return this;
        }

        public DecoratedSearchResults build() {
            return new DecoratedSearchResults(this.params, this.problems, this.evalResult, this.languageIsoCode, this.results, this.focusDomain, this.focusDomainId, this.filters, this.resultPages);
        }
    }
}
