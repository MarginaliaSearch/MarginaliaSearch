package nu.marginalia.search.model;

import javax.annotation.Nullable;

public enum SearchTitleParameter {
    DEFAULT("default"),
    TITLE("title");

    public final String value;

    SearchTitleParameter(String value) {
        this.value = value;
    }

    public static SearchTitleParameter parse(@Nullable String value) {
        if (TITLE.value.equals(value)) return TITLE;

        return DEFAULT;
    }

}
