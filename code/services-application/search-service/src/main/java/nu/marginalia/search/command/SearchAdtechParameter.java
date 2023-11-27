package nu.marginalia.search.command;

import nu.marginalia.index.client.model.query.SearchSubquery;

import javax.annotation.Nullable;
import java.util.Arrays;

public enum SearchAdtechParameter {
    DEFAULT("default"),
    REDUCE("reduce", "special:ads", "special:affiliate");

    public final String value;
    public final String[] implictExcludeSearchTerms;

    SearchAdtechParameter(String value, String... implictExcludeSearchTerms) {
        this.value = value;
        this.implictExcludeSearchTerms = implictExcludeSearchTerms;
    }

    public static SearchAdtechParameter parse(@Nullable String value) {
        if (REDUCE.value.equals(value)) return REDUCE;

        return DEFAULT;
    }

    public void addTacitTerms(SearchSubquery subquery) {
        subquery.searchTermsExclude.addAll(Arrays.asList(implictExcludeSearchTerms));
    }
}
