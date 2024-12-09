package nu.marginalia.search.model;

import nu.marginalia.search.command.SearchParameters;

public record SearchErrorMessageModel(String errorTitle, String errorRest, SearchParameters parameters, SearchFilters filters) {
}
