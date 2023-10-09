package nu.marginalia.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

import java.util.List;

@AllArgsConstructor
@Getter
@With
public class ApiSearchResults {
    private final String license;

    private final String query;
    private final List<ApiSearchResult> results;
}
