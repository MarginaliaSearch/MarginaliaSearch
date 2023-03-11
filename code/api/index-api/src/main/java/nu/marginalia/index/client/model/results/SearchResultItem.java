package nu.marginalia.index.client.model.results;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;

import java.util.ArrayList;
import java.util.List;

/** Represents a document matching a search query */
@AllArgsConstructor @Getter
public class SearchResultItem {
    /** Encoded ID that contains both the URL id and its ranking */
    public final long combinedId;

    /** How did the subqueries match against the document ? */
    public final List<SearchResultKeywordScore> scores;

    /** How many other potential results existed in the same domain */
    public int resultsFromDomain;

    public SearchResultItem(long val) {
        this.combinedId = val;
        this.scores = new ArrayList<>(16);
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

    /* Used for evaluation */
    private transient double scoreValue = 1;
    public void setScore(double score) {
        scoreValue = score;
    }
    public double getScore() {
        return scoreValue;
    }

    private transient int domainId = Integer.MIN_VALUE;
    public void setDomainId(int domainId) {
        this.domainId = domainId;
    }
    public int getDomainId() {
        return this.domainId;
    }

    public int hashCode() {
        return getUrlIdInt();
    }

    public String toString() {
        return getClass().getSimpleName() + "[ url= " + getUrlId() + ", rank=" + getRanking() + "]";
    }

    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (other == this)
            return true;
        if (other instanceof SearchResultItem o) {
            return o.getUrlIdInt()  == getUrlIdInt();
        }
        return false;
    }

    public long deduplicationKey() {
        final int domainId = getDomainId();

        if (domainId == Integer.MAX_VALUE || domainId == Integer.MIN_VALUE) {
            return 0;
        }

        return domainId;
    }
}
