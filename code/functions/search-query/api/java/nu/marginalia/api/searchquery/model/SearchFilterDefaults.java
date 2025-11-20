package nu.marginalia.api.searchquery.model;

import nu.marginalia.api.searchquery.QueryFilterSpec;

public enum SearchFilterDefaults {
    POPULAR("default.xml"),
    SMALLWEB("small-web.xml"),
    BLOGOSPHERE("blogs.xml"),
    NO_FILTER("no-filter.xml"),
    VINTAGE("vintage.xml"),
    TILDE("tilde.xml"),
    ACADEMIA("academia.xml"),
    PLAIN_TEXT("plain-text.xml"),
    FOOD("food.xml"),
    FORUM("forum.xml"),
    WIKI("wiki.xml"),
    DOCS("docs.xml");

    SearchFilterDefaults(String fileName) {
        this.fileName = fileName;
    }

    public final String fileName;

    public static final String SYSTEM_USER_ID = "SYSTEM";
    public static final String SYSTEM_DEFAULT_FILTER = NO_FILTER.name();

    public QueryFilterSpec.FilterByName asFilterSpec() {
        return new QueryFilterSpec.FilterByName(SYSTEM_USER_ID, name());
    }
}
