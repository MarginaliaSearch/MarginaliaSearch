package nu.marginalia.wmsa.edge.index.journal.model;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.id.EdgeId;

public record SearchIndexJournalEntryHeader(int entrySize, long documentId, IndexBlock block) {

    public static final int HEADER_SIZE_LONGS = 2;

    public SearchIndexJournalEntryHeader( EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId, IndexBlock block) {
        this(-1, combineIds(domainId, urlId), block);
    }

    private static long combineIds(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId) {
        long did = domainId.id();
        long uid = urlId.id();

        return (did << 32L) | uid;
    }

}
