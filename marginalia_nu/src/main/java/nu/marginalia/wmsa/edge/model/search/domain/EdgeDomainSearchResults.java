package nu.marginalia.wmsa.edge.model.search.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeIdList;

@AllArgsConstructor @Getter @ToString
public class EdgeDomainSearchResults {
    public final String keyword;
    public final EdgeIdList<EdgeUrl> results;
}
