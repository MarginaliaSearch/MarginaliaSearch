package nu.marginalia.index.client.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeIdList;

@AllArgsConstructor @Getter @ToString
public class EdgeDomainSearchResults {
    public final String keyword;
    public final EdgeIdList<EdgeUrl> results;
}
