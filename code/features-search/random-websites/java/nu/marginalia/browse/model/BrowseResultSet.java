package nu.marginalia.browse.model;

import java.util.Collection;

public record BrowseResultSet(Collection<BrowseResult> results, String focusDomain) {
    public BrowseResultSet(Collection<BrowseResult> results) {
        this(results, "");
    }

    public boolean hasFocusDomain() {
        return focusDomain != null && !focusDomain.isBlank();
    }
}
