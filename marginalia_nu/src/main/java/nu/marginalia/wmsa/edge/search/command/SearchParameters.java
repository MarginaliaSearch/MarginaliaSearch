package nu.marginalia.wmsa.edge.search.command;

import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;

public record SearchParameters(EdgeSearchProfile profile, SearchJsParameter js, boolean detailedResults) {
    public String profileStr() {
        return profile.name;
    }
}
