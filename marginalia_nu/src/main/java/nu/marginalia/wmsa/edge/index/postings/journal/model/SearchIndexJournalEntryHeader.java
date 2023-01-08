package nu.marginalia.wmsa.edge.index.postings.journal.model;

import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;

public record SearchIndexJournalEntryHeader(int entrySize, long documentId, long documentMeta) {

    public static final int HEADER_SIZE_LONGS = 3;

    public SearchIndexJournalEntryHeader( EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId, long documentMeta) {
        this(-1, combineIds(domainId, urlId), documentMeta);
    }

    private static long combineIds(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId) {
        long did = domainId.id();
        long uid = urlId.id();

        return (did << 32L) | uid;
    }

}
