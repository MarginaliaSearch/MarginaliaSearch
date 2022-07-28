package nu.marginalia.wmsa.edge.search.query.model;

import nu.marginalia.wmsa.edge.search.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.command.SearchJsParameter;

public record EdgeUserSearchParameters (String humanQuery, EdgeSearchProfile profile, SearchJsParameter jsSetting){
}
