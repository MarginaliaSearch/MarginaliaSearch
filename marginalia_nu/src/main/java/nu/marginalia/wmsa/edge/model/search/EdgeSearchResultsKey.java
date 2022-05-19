package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@AllArgsConstructor
@Getter
public class EdgeSearchResultsKey {
    public final int bucket;
    public final int searchTermCount;
}
