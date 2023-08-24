package nu.marginalia.index.client.model.results;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.model.id.UrlIdCodec;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Represents a document matching a search query */
@AllArgsConstructor @Getter
public class SearchResultItem implements Comparable<SearchResultItem> {
    /** Encoded ID that contains both the URL id and its ranking */
    public final long combinedId;

    /** How did the subqueries match against the document ? */
    public final List<SearchResultKeywordScore> keywordScores;

    /** How many other potential results existed in the same domain */
    public int resultsFromDomain;

    public SearchResultItem(long val) {
        this.combinedId = val;
        this.keywordScores = new ArrayList<>(16);
    }


    public long getDocumentId() {
        return UrlIdCodec.removeRank(combinedId);
    }

    public int getRanking() {
        return UrlIdCodec.getRank(combinedId);
    }

    /* Used for evaluation */
    private transient SearchResultPreliminaryScore scoreValue = null;
    public void setScore(SearchResultPreliminaryScore score) {
        scoreValue = score;
    }
    public SearchResultPreliminaryScore getScore() {
        return scoreValue;
    }

    public int getDomainId() {
        return UrlIdCodec.getDomainId(this.combinedId);
    }

    public int hashCode() {
        return Long.hashCode(combinedId);
    }

    public String toString() {
        return getClass().getSimpleName() + "[ url= " + getDocumentId() + ", rank=" + getRanking() + "]";
    }

    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (other == this)
            return true;
        if (other instanceof SearchResultItem o) {
            return o.getDocumentId()  == getDocumentId();
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

    @Override
    public int compareTo(@NotNull SearchResultItem o) {
        // this looks like a bug, but we actually want this in a reversed order
        int diff = o.getScore().compareTo(getScore());
        if (diff != 0)
            return diff;

        return Long.compare(this.combinedId, o.combinedId);
    }
}
