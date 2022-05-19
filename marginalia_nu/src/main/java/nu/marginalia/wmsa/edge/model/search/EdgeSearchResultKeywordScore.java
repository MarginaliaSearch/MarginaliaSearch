package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

@AllArgsConstructor @ToString @EqualsAndHashCode
public class EdgeSearchResultKeywordScore {
    public final String keyword;
    public final IndexBlock index;
    public boolean title;
    public boolean link;
}
