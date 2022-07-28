package nu.marginalia.wmsa.edge.search.command;

import javax.annotation.Nullable;

public enum SearchJsParameter {
    DEFAULT("default"),
    DENY_JS("no-js", "js:true"),
    REQUIRE_JS("yes-js", "js:false");

    public final String value;
    public final String[] implictExcludeSearchTerms;

    SearchJsParameter(String value, String... implictExcludeSearchTerms) {
        this.value = value;
        this.implictExcludeSearchTerms = implictExcludeSearchTerms;
    }

    public static SearchJsParameter parse(@Nullable String value) {
        if (DENY_JS.value.equals(value)) return DENY_JS;
        if (REQUIRE_JS.value.equals(value)) return REQUIRE_JS;

        return DEFAULT;
    }
}
