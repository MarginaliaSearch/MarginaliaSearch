package nu.marginalia.wmsa.edge.search;

import lombok.Data;
import lombok.EqualsAndHashCode;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

@Data @EqualsAndHashCode
public class BrowseResult {
    public final EdgeUrl url;
    public final int domainId;
}
