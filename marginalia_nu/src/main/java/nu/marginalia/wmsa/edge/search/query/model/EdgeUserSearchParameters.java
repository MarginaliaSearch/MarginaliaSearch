package nu.marginalia.wmsa.edge.search.query.model;

import nu.marginalia.wmsa.edge.search.command.SearchJsParameter;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;

public record EdgeUserSearchParameters (String humanQuery, EdgeSearchProfile profile, SearchJsParameter jsSetting) {
}
