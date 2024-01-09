package nu.marginalia.search.command;

import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.query.limit.SpecificationLimit;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.Arrays;

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

    public SpecificationLimit yearLimit() {
        if (this == RECENT) {
            return SpecificationLimit.greaterThan(LocalDateTime.now().getYear() - 1);
        } else {
            return SpecificationLimit.none();
        }
    }
}
