package nu.marginalia.wmsa.edge.model.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor @ToString @Getter
public class EdgeSearchResultItem {
    public final int bucketId;
    public final long combinedId; // this isn't the external domain ID, but a ranking
    public final List<EdgeSearchResultKeywordScore> scores;

    public EdgeSearchResultItem(int bucketId, long val) {
        this.bucketId = bucketId;

        combinedId = val;
        scores = new ArrayList<>(16);
    }

    public EdgeId<EdgeUrl> getUrlId() {
        return new EdgeId<>(getUrlIdInt());
    }

    public int getUrlIdInt() {
        return (int)(combinedId & 0xFFFF_FFFFL);
    }
    public int getRanking() {
        return (int)(combinedId >>> 32);
    }

    private transient double scoreValue = 1;
    public void setScore(double score) {
        scoreValue = score;
    }
    public double getScore() {
        return scoreValue;
    }

    public int hashCode() {
        return getUrlIdInt();
    }

    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (other == this)
            return true;
        if (other instanceof EdgeSearchResultItem o) {
            return o.getUrlIdInt()  == getUrlIdInt();
        }
        return false;
    }
}
