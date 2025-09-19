package nu.marginalia.search.model;

public record SearchErrorMessageModel(String errorTitle, String errorRest, SearchParameters parameters, SearchFilters filters) {
}
