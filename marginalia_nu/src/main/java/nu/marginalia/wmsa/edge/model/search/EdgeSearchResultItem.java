package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor @ToString @Getter @EqualsAndHashCode
public class EdgeSearchResultItem {
    public final int blockId;
    public final int queryLength;
    public final EdgeId<EdgeDomain> domain;
    public final EdgeId<EdgeUrl> url;
    public final List<EdgeSearchResultKeywordScore> scores;

    public EdgeSearchResultItem(int blockId, int queryLength, long val) {
        int urlId = (int) (val & 0xFFFFFFFFL);
        int domainId = (int) (val >>> 32);

        this.queryLength = queryLength;
        this.blockId = blockId;

        url = new EdgeId<>(urlId);
        domain = new EdgeId<>(domainId);
        scores = new ArrayList<>();
    }

    public long getCombinedId() {
        return ((long) domain.id() << 32L) | url.id();
    }

}
