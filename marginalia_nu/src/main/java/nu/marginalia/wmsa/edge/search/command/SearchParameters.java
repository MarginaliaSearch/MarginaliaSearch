package nu.marginalia.wmsa.edge.search.command;

import nu.marginalia.wmsa.edge.search.EdgeSearchProfile;

public record SearchParameters(EdgeSearchProfile profile, String js) {
    public String profileStr() {
        return profile.name;
    }
}
