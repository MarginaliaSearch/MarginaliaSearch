package nu.marginalia.search.command;

import nu.marginalia.api.searchquery.model.query.SearchQuery;

import javax.annotation.Nullable;
import java.util.Arrays;

public enum SearchJsParameter {
    DEFAULT("default"),
    DENY_JS("no-js", "special:scripts");

    public final String value;
    public final String[] implictExcludeSearchTerms;

    SearchJsParameter(String value, String... implictExcludeSearchTerms) {
        this.value = value;
        this.implictExcludeSearchTerms = implictExcludeSearchTerms;
    }

    public static SearchJsParameter parse(@Nullable String value) {
        if (DENY_JS.value.equals(value)) return DENY_JS;

        return DEFAULT;
    }

    public void addTacitTerms(SearchQuery subquery) {
        subquery.searchTermsExclude.addAll(Arrays.asList(implictExcludeSearchTerms));
    }
}
