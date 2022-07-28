package nu.marginalia.wmsa.edge.model.search.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.List;

@AllArgsConstructor @Getter @ToString
public class EdgeDomainSearchResults {
    public final String keyword;
    public final List<EdgeId<EdgeUrl>> results;
}
