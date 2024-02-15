package nu.marginalia.search.command;

import javax.annotation.Nullable;

public enum SearchRecentParameter {
    DEFAULT("default"),
    RECENT("recent");

    public final String value;

    SearchRecentParameter(String value) {
        this.value = value;
    }

    public static SearchRecentParameter parse(@Nullable String value) {
        if (RECENT.value.equals(value)) return RECENT;

        return DEFAULT;
    }

}
