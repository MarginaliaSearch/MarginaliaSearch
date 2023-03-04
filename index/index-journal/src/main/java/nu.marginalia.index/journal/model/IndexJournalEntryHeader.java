package nu.marginalia.index.journal.model;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;

public record IndexJournalEntryHeader(int entrySize, long combinedId, long documentMeta) {

    public IndexJournalEntryHeader(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId, long documentMeta) {
        this(-1, combineIds(domainId, urlId), documentMeta);
    }

    static long combineIds(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId) {
        long did = domainId.id();
        long uid = urlId.id();

        return (did << 32L) | uid;
    }

}
