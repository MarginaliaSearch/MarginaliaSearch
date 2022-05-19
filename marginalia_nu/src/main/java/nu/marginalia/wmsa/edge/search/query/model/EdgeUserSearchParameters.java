package nu.marginalia.wmsa.edge.search.query.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.wmsa.edge.search.EdgeSearchProfile;

@AllArgsConstructor @Getter
public class EdgeUserSearchParameters {
    public final String humanQuery;
    public final EdgeSearchProfile profile;
    public final String jsSetting;
}
