package nu.marginalia.api.searchquery.model.query;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchQuery {
    /**
     * An infix style expression that encodes the required terms in the query
     */
    public final String compiledQuery;

    /**
     * All terms that appear in {@see compiledQuery}
     */
    public final List<String> searchTermsInclude;

    /**
     * These terms must be absent from the document
     */
    public final List<String> searchTermsExclude;

    /**
     * These terms must be present in the document, but are not used in ranking
     */
    public final List<String> searchTermsAdvice;

    /**
     * If these optional terms are present in the document, rank it highly
     */
    public final List<String> searchTermsPriority;

    /**
     * Terms that we require to be in the same sentence
     */
    public final List<SearchPhraseConstraint> phraseConstraints;

    @Deprecated // why does this exist?
    private double value = 0;

    public SearchQuery(String compiledQuery, List<String> searchTermsInclude, List<String> searchTermsExclude, List<String> searchTermsAdvice, List<String> searchTermsPriority, List<SearchPhraseConstraint> phraseConstraints, double value) {
        this.compiledQuery = compiledQuery;
        this.searchTermsInclude = searchTermsInclude;
        this.searchTermsExclude = searchTermsExclude;
        this.searchTermsAdvice = searchTermsAdvice;
        this.searchTermsPriority = searchTermsPriority;
        this.phraseConstraints = phraseConstraints;
        this.value = value;
    }

    public static SearchQueryBuilder builder() {
        return new SearchQueryBuilder();
    }

    public SearchQuery() {
        this.compiledQuery = "";
        this.searchTermsInclude = new ArrayList<>();
        this.searchTermsExclude = new ArrayList<>();
        this.searchTermsAdvice = new ArrayList<>();
        this.searchTermsPriority = new ArrayList<>();
        this.phraseConstraints = new ArrayList<>();
    }

    public SearchQuery(String compiledQuery,
                       List<String> searchTermsInclude,
                       List<String> searchTermsExclude,
                       List<String> searchTermsAdvice,
                       List<String> searchTermsPriority,
                       List<SearchPhraseConstraint> phraseConstraints) {
        this.compiledQuery = compiledQuery;
        this.searchTermsInclude = searchTermsInclude;
        this.searchTermsExclude = searchTermsExclude;
        this.searchTermsAdvice = searchTermsAdvice;
        this.searchTermsPriority = searchTermsPriority;
        this.phraseConstraints = phraseConstraints;
    }

    @Deprecated // why does this exist?
    public SearchQuery setValue(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            this.value = Double.MAX_VALUE;
        } else {
            this.value = value;
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!compiledQuery.isEmpty()) sb.append("compiledQuery=").append(compiledQuery).append(", ");
        if (!searchTermsExclude.isEmpty())
            sb.append("exclude=").append(searchTermsExclude.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermsAdvice.isEmpty())
            sb.append("advice=").append(searchTermsAdvice.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermsPriority.isEmpty())
            sb.append("priority=").append(searchTermsPriority.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!phraseConstraints.isEmpty())
            sb.append("phraseConstraints=").append(phraseConstraints.stream().map(coh -> coh.terms().stream().collect(Collectors.joining(",", "[", "] "))).collect(Collectors.joining(", ")));

        return sb.toString();
    }

    public String getCompiledQuery() {
        return this.compiledQuery;
    }

    public List<String> getSearchTermsInclude() {
        return this.searchTermsInclude;
    }

    public List<String> getSearchTermsExclude() {
        return this.searchTermsExclude;
    }

    public List<String> getSearchTermsAdvice() {
        return this.searchTermsAdvice;
    }

    public List<String> getSearchTermsPriority() {
        return this.searchTermsPriority;
    }

    public List<SearchPhraseConstraint> getPhraseConstraints() {
        return this.phraseConstraints;
    }

    @Deprecated
    public double getValue() {
        return this.value;
    }

    public SearchQuery withCompiledQuery(String compiledQuery) {
        return this.compiledQuery == compiledQuery ? this : new SearchQuery(compiledQuery, this.searchTermsInclude, this.searchTermsExclude, this.searchTermsAdvice, this.searchTermsPriority, this.phraseConstraints, this.value);
    }

    public SearchQuery withSearchTermsInclude(List<String> searchTermsInclude) {
        return this.searchTermsInclude == searchTermsInclude ? this : new SearchQuery(this.compiledQuery, searchTermsInclude, this.searchTermsExclude, this.searchTermsAdvice, this.searchTermsPriority, this.phraseConstraints, this.value);
    }

    public SearchQuery withSearchTermsExclude(List<String> searchTermsExclude) {
        return this.searchTermsExclude == searchTermsExclude ? this : new SearchQuery(this.compiledQuery, this.searchTermsInclude, searchTermsExclude, this.searchTermsAdvice, this.searchTermsPriority, this.phraseConstraints, this.value);
    }

    public SearchQuery withSearchTermsAdvice(List<String> searchTermsAdvice) {
        return this.searchTermsAdvice == searchTermsAdvice ? this : new SearchQuery(this.compiledQuery, this.searchTermsInclude, this.searchTermsExclude, searchTermsAdvice, this.searchTermsPriority, this.phraseConstraints, this.value);
    }

    public SearchQuery withSearchTermsPriority(List<String> searchTermsPriority) {
        return this.searchTermsPriority == searchTermsPriority ? this : new SearchQuery(this.compiledQuery, this.searchTermsInclude, this.searchTermsExclude, this.searchTermsAdvice, searchTermsPriority, this.phraseConstraints, this.value);
    }

    public SearchQuery withPhraseConstraints(List<SearchPhraseConstraint> phraseConstraints) {
        return this.phraseConstraints == phraseConstraints ? this : new SearchQuery(this.compiledQuery, this.searchTermsInclude, this.searchTermsExclude, this.searchTermsAdvice, this.searchTermsPriority, phraseConstraints, this.value);
    }

    public SearchQuery withValue(double value) {
        return this.value == value ? this : new SearchQuery(this.compiledQuery, this.searchTermsInclude, this.searchTermsExclude, this.searchTermsAdvice, this.searchTermsPriority, this.phraseConstraints, value);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof SearchQuery)) return false;
        final SearchQuery other = (SearchQuery) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$compiledQuery = this.getCompiledQuery();
        final Object other$compiledQuery = other.getCompiledQuery();
        if (this$compiledQuery == null ? other$compiledQuery != null : !this$compiledQuery.equals(other$compiledQuery))
            return false;
        final Object this$searchTermsInclude = this.getSearchTermsInclude();
        final Object other$searchTermsInclude = other.getSearchTermsInclude();
        if (this$searchTermsInclude == null ? other$searchTermsInclude != null : !this$searchTermsInclude.equals(other$searchTermsInclude))
            return false;
        final Object this$searchTermsExclude = this.getSearchTermsExclude();
        final Object other$searchTermsExclude = other.getSearchTermsExclude();
        if (this$searchTermsExclude == null ? other$searchTermsExclude != null : !this$searchTermsExclude.equals(other$searchTermsExclude))
            return false;
        final Object this$searchTermsAdvice = this.getSearchTermsAdvice();
        final Object other$searchTermsAdvice = other.getSearchTermsAdvice();
        if (this$searchTermsAdvice == null ? other$searchTermsAdvice != null : !this$searchTermsAdvice.equals(other$searchTermsAdvice))
            return false;
        final Object this$searchTermsPriority = this.getSearchTermsPriority();
        final Object other$searchTermsPriority = other.getSearchTermsPriority();
        if (this$searchTermsPriority == null ? other$searchTermsPriority != null : !this$searchTermsPriority.equals(other$searchTermsPriority))
            return false;
        final Object this$phraseConstraints = this.getPhraseConstraints();
        final Object other$phraseConstraints = other.getPhraseConstraints();
        if (this$phraseConstraints == null ? other$phraseConstraints != null : !this$phraseConstraints.equals(other$phraseConstraints))
            return false;
        if (Double.compare(this.getValue(), other.getValue()) != 0) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SearchQuery;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $compiledQuery = this.getCompiledQuery();
        result = result * PRIME + ($compiledQuery == null ? 43 : $compiledQuery.hashCode());
        final Object $searchTermsInclude = this.getSearchTermsInclude();
        result = result * PRIME + ($searchTermsInclude == null ? 43 : $searchTermsInclude.hashCode());
        final Object $searchTermsExclude = this.getSearchTermsExclude();
        result = result * PRIME + ($searchTermsExclude == null ? 43 : $searchTermsExclude.hashCode());
        final Object $searchTermsAdvice = this.getSearchTermsAdvice();
        result = result * PRIME + ($searchTermsAdvice == null ? 43 : $searchTermsAdvice.hashCode());
        final Object $searchTermsPriority = this.getSearchTermsPriority();
        result = result * PRIME + ($searchTermsPriority == null ? 43 : $searchTermsPriority.hashCode());
        final Object $phraseConstraints = this.getPhraseConstraints();
        result = result * PRIME + ($phraseConstraints == null ? 43 : $phraseConstraints.hashCode());
        final long $value = Double.doubleToLongBits(this.getValue());
        result = result * PRIME + (int) ($value >>> 32 ^ $value);
        return result;
    }

    public static class SearchQueryBuilder {
        private String compiledQuery;
        public final List<String> searchTermsInclude = new ArrayList<>();
        public final List<String> searchTermsExclude = new ArrayList<>();
        public final List<String> searchTermsAdvice = new ArrayList<>();
        public final List<String> searchTermsPriority = new ArrayList<>();
        public final List<SearchPhraseConstraint> searchPhraseConstraints = new ArrayList<>();

        private SearchQueryBuilder() {
        }

        public SearchQueryBuilder compiledQuery(String query) {
            this.compiledQuery = query;
            return this;
        }

        public SearchQueryBuilder include(String... terms) {
            searchTermsInclude.addAll(List.of(terms));
            return this;
        }

        public SearchQueryBuilder exclude(String... terms) {
            searchTermsExclude.addAll(List.of(terms));
            return this;
        }

        public SearchQueryBuilder advice(String... terms) {
            searchTermsAdvice.addAll(List.of(terms));
            return this;
        }

        public SearchQueryBuilder priority(String... terms) {
            searchTermsPriority.addAll(List.of(terms));
            return this;
        }

        public SearchQueryBuilder phraseConstraint(SearchPhraseConstraint constraint) {
            searchPhraseConstraints.add(constraint);
            return this;
        }

        public SearchQuery build() {
            return new SearchQuery(compiledQuery, searchTermsInclude, searchTermsExclude, searchTermsAdvice, searchTermsPriority, searchPhraseConstraints);
        }

        /**
         * If there are no ranking terms, promote the advice terms to ranking terms
         */
        public void promoteNonRankingTerms() {
            if (searchTermsInclude.isEmpty() && !searchTermsAdvice.isEmpty()) {
                searchTermsInclude.addAll(searchTermsAdvice);
                searchTermsAdvice.clear();
            }
        }
    }
}
