package nu.marginalia.wmsa.edge.search.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@Data @EqualsAndHashCode
public class BrowseResult {
    public final EdgeUrl url;
    public final int domainId;

    public String domainHash() {
        var domain = url.domain;
        if ("www".equals(domain.subDomain)) {
            return domain.domain;
        }
        return domain.toString();
    }
}
