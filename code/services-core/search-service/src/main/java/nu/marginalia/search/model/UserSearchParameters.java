package nu.marginalia.search.model;

import nu.marginalia.search.command.SearchJsParameter;
import nu.marginalia.search.model.SearchProfile;

public record UserSearchParameters(String humanQuery, SearchProfile profile, SearchJsParameter jsSetting) {
}
