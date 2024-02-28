package nu.marginalia.keyword.model;

/** Pointer into a {@see DocumentKeywords}.  It starts out before the first position,
 * forward with advancePointer().
 * */
public class DocumentKeywordsPointer {
    private int pos = -1;

    private final DocumentKeywords keywords;

    DocumentKeywordsPointer(DocumentKeywords keywords) {
        this.keywords = keywords;
    }

    /** Number of positions remaining */
    public int remaining() {
        return keywords.size() - Math.max(0, pos);
    }

    /** Return the keyword associated with the current position */
    public String getKeyword() {
        return keywords.keywords[pos];
    }

    /** Return the metadata associated with the current position */
    public long getMetadata() {
        return keywords.metadata[pos];
    }

    /** Advance the current position,
     * returns false if this was the
     * last position */
    public boolean advancePointer() {
        return ++pos < keywords.size();
    }

    /** Returns true unless the pointer is beyond the last position in the keyword set */
    public boolean hasMore() {
        return pos + 1 < keywords.size();
    }
}
