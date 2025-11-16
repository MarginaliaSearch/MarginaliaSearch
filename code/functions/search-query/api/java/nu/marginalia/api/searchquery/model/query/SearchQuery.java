package nu.marginalia.api.searchquery.model.query;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
     * Weight for searchTermsPriority
     */
    public final FloatList searchTermsPriorityWeight;

    /**
     * Terms that we require to be in the same sentence
     */
    public final List<SearchPhraseConstraint> phraseConstraints;

    public SearchQuery(String compiledQuery,
                       List<String> searchTermsInclude,
                       List<String> searchTermsExclude,
                       List<String> searchTermsAdvice,
                       List<String> searchTermsPriority,
                       FloatList searchTermsPriorityWeight,
                       List<SearchPhraseConstraint> phraseConstraints) {
        this.compiledQuery = compiledQuery;
        this.searchTermsInclude = searchTermsInclude;
        this.searchTermsExclude = searchTermsExclude;
        this.searchTermsAdvice = searchTermsAdvice;
        this.searchTermsPriority = searchTermsPriority;
        this.searchTermsPriorityWeight = searchTermsPriorityWeight;
        this.phraseConstraints = phraseConstraints;
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
        this.searchTermsPriorityWeight = new FloatArrayList();
        this.phraseConstraints = new ArrayList<>();
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


    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchQuery that)) return false;

        return Objects.equals(compiledQuery, that.compiledQuery)
                && Objects.equals(searchTermsInclude, that.searchTermsInclude)
                && Objects.equals(searchTermsExclude, that.searchTermsExclude)
                && Objects.equals(searchTermsAdvice, that.searchTermsAdvice)
                && Objects.equals(searchTermsPriority, that.searchTermsPriority)
                && Objects.equals(phraseConstraints, that.phraseConstraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(compiledQuery,
                                searchTermsInclude,
                                searchTermsExclude,
                                searchTermsAdvice,
                                searchTermsPriority,
                                phraseConstraints);
    }

    public static class SearchQueryBuilder {
        private String compiledQuery;
        public final List<String> searchTermsInclude = new ArrayList<>();
        public final List<String> searchTermsExclude = new ArrayList<>();
        public final List<String> searchTermsAdvice = new ArrayList<>();
        public final List<String> searchTermsPriority = new ArrayList<>();
        public final FloatList searchTermsPriorityWeight = new FloatArrayList();
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

        public SearchQueryBuilder priority(String term, float weight) {
            searchTermsPriority.add(term);
            searchTermsPriorityWeight.add(weight);
            return this;
        }

        public SearchQueryBuilder phraseConstraint(SearchPhraseConstraint constraint) {
            searchPhraseConstraints.add(constraint);
            return this;
        }

        public SearchQuery build() {
            return new SearchQuery(compiledQuery, searchTermsInclude, searchTermsExclude, searchTermsAdvice, searchTermsPriority, searchTermsPriorityWeight, searchPhraseConstraints);
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
