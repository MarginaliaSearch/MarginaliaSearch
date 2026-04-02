package nu.marginalia.search.model;

import javax.annotation.Nullable;

public enum SearchNsfwParameter {
    NO_FILTER("off"),
    DO_FILTER("smut");
    public final String value;

    SearchNsfwParameter(String value) {
        this.value = value;
    }

    public static SearchNsfwParameter parse(@Nullable String value) {
        if (DO_FILTER.value.equals(value)) return DO_FILTER;

        return NO_FILTER;
    }

}
