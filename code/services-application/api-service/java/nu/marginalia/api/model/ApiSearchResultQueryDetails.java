package nu.marginalia.api.model;

import java.util.Set;

public record ApiSearchResultQueryDetails(String keyword, int count, Set<String> flagsUnstableAPI) {}
