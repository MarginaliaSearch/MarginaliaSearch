package nu.marginalia.api.searchquery.model.query;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@With
@EqualsAndHashCode
public class SearchQuery {

    /** An infix style expression that encodes the required terms in the query */
    public final String compiledQuery;

    /** All terms that appear in {@see compiledQuery} */
    public final List<String> searchTermsInclude;

    /** These terms must be absent from the document */
    public final List<String> searchTermsExclude;

    /** These terms must be present in the document, but are not used in ranking */
    public final List<String> searchTermsAdvice;

    /** If these optional terms are present in the document, rank it highly */
    public final List<String> searchTermsPriority;

    /** Terms that we require to be in the same sentence */
    public final List<List<String>> searchTermCoherences;

    @Deprecated // why does this exist?
    private double value = 0;

    public SearchQuery() {
        this.compiledQuery = "";
        this.searchTermsInclude = new ArrayList<>();
        this.searchTermsExclude = new ArrayList<>();
        this.searchTermsAdvice = new ArrayList<>();
        this.searchTermsPriority = new ArrayList<>();
        this.searchTermCoherences = new ArrayList<>();
    }

    public SearchQuery(String compiledQuery,
                       List<String> searchTermsInclude,
                       List<String> searchTermsExclude,
                       List<String> searchTermsAdvice,
                       List<String> searchTermsPriority,
                       List<List<String>> searchTermCoherences) {
        this.compiledQuery = compiledQuery;
        this.searchTermsInclude = searchTermsInclude;
        this.searchTermsExclude = searchTermsExclude;
        this.searchTermsAdvice = searchTermsAdvice;
        this.searchTermsPriority = searchTermsPriority;
        this.searchTermCoherences = searchTermCoherences;
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
        if (!compiledQuery.isEmpty()) sb.append("compiledQuery=").append(compiledQuery);
        if (!searchTermsExclude.isEmpty()) sb.append("exclude=").append(searchTermsExclude.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermsAdvice.isEmpty()) sb.append("advice=").append(searchTermsAdvice.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermsPriority.isEmpty()) sb.append("priority=").append(searchTermsPriority.stream().collect(Collectors.joining(",", "[", "] ")));
        if (!searchTermCoherences.isEmpty()) sb.append("coherences=").append(searchTermCoherences.stream().map(coh->coh.stream().collect(Collectors.joining(",", "[", "] "))).collect(Collectors.joining(", ")));

        return sb.toString();
    }


}
