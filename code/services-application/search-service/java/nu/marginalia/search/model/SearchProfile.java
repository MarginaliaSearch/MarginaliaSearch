package nu.marginalia.search.model;

import nu.marginalia.api.searchquery.model.SearchFilterDefaults;

import java.util.Objects;

public enum SearchProfile {
    POPULAR("default", SearchFilterDefaults.NO_FILTER),
    SMALLWEB("modern", SearchFilterDefaults.SMALLWEB),
    BLOGOSPHERE("blogosphere", SearchFilterDefaults.BLOGOSPHERE),
    NO_FILTER("corpo", SearchFilterDefaults.NO_FILTER),
    VINTAGE("vintage", SearchFilterDefaults.VINTAGE),
    TILDE("tilde", SearchFilterDefaults.TILDE),
    ACADEMIA("academia",  SearchFilterDefaults.ACADEMIA),
    PLAIN_TEXT("plain-text", SearchFilterDefaults.PLAIN_TEXT),
    FOOD("food", SearchFilterDefaults.FOOD),
    FORUM("forum", SearchFilterDefaults.FORUM),
    WIKI("wiki", SearchFilterDefaults.WIKI),
    DOCS("docs", SearchFilterDefaults.DOCS),
    ;


    public final String filterId;
    public final SearchFilterDefaults defaultFilter;

    SearchProfile(String filterId, SearchFilterDefaults defaultFilter) {
        this.filterId = filterId;
        this.defaultFilter = defaultFilter;
    }

    private final static SearchProfile[] values = values();

    public static SearchProfile getSearchProfile(String param) {
        if (null == param) {
            return NO_FILTER;
        }

        for (var profile : values) {
            if (Objects.equals(profile.filterId, param)) {
                return profile;
            }
        }

        return NO_FILTER;
    }

}

