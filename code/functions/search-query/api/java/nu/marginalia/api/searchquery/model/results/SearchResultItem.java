package nu.marginalia.api.searchquery.model.results;

import lombok.AllArgsConstructor;
import lombok.Getter;
import nu.marginalia.model.id.UrlIdCodec;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Represents a document matching a search query */
@AllArgsConstructor @Getter
public class SearchResultItem implements Comparable<SearchResultItem> {
    /** Encoded ID that contains both the URL id and its ranking.  This is
     * probably not what you want, use getDocumentId() instead */
    public final long combinedId;

    /** Encoded document metadata */
    public final long encodedDocMetadata;

    /** Encoded html features of document */

    public final int htmlFeatures;

    /** How did the subqueries match against the document ? */
    public final List<SearchResultKeywordScore> keywordScores;

    /** How many other potential results existed in the same domain */
    public int resultsFromDomain;

    public boolean hasPrioTerm;

    public SearchResultItem(long combinedId,
                            long encodedDocMetadata,
                            int htmlFeatures,
                            boolean hasPrioTerm) {
        this.combinedId = combinedId;
        this.encodedDocMetadata = encodedDocMetadata;
        this.keywordScores = new ArrayList<>();
        this.htmlFeatures = htmlFeatures;
        this.hasPrioTerm = hasPrioTerm;
    }


    public long getDocumentId() {
        return UrlIdCodec.removeRank(combinedId);
    }

    public int getRanking() {
        return UrlIdCodec.getRank(combinedId);
    }

    /* Used for evaluation */
    private transient double scoreValue = Double.MAX_VALUE;
    public void setScore(double score) {
        scoreValue = score;
    }
    public double getScore() {
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

    @Override
    public int compareTo(@NotNull SearchResultItem o) {
        // this looks like a bug, but we actually want this in a reversed order
        int diff = Double.compare(getScore(), o.getScore());
        if (diff != 0)
            return diff;

        return Long.compare(this.combinedId, o.combinedId);
    }


}
