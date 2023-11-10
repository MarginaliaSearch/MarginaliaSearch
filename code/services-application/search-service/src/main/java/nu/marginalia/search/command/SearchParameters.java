package nu.marginalia.search.command;

import nu.marginalia.search.model.SearchProfile;

public record SearchParameters(String query, SearchProfile profile, SearchJsParameter js) {
    public String profileStr() {
        return profile.name;
    }
}
