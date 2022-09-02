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
    public final int bucketId;
    public final int queryLength;
    public final EdgeId<EdgeDomain> domain; // this isn't the external domain ID, but a ranking
    public final EdgeId<EdgeUrl> url;
    public final List<EdgeSearchResultKeywordScore> scores;

    public EdgeSearchResultItem(int bucketId, int queryLength, long val) {
        int urlId = (int) (val & 0xFFFF_FFFFL);
        int domainId = (int) (val >>> 32);

        this.queryLength = queryLength;
        this.bucketId = bucketId;

        url = new EdgeId<>(urlId);
        domain = new EdgeId<>(domainId);
        scores = new ArrayList<>();
    }

    public long getCombinedId() {
        return ((long) domain.id() << 32L) | url.id();
    }

}
