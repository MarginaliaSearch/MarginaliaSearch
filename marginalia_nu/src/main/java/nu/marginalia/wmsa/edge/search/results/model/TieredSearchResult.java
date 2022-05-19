package nu.marginalia.wmsa.edge.search.results.model;

import lombok.AllArgsConstructor;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;

@AllArgsConstructor
public class TieredSearchResult {
    public final int length;
    public final IndexBlock block;
    public final EdgeUrlDetails details;
}
