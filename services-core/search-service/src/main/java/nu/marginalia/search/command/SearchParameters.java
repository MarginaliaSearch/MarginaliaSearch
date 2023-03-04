package nu.marginalia.search.command;

import nu.marginalia.search.model.SearchProfile;

public record SearchParameters(SearchProfile profile, SearchJsParameter js, boolean detailedResults) {
    public String profileStr() {
        return profile.name;
    }
}
