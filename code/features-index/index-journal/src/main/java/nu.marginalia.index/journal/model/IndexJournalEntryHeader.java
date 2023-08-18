package nu.marginalia.index.journal.model;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;

public record IndexJournalEntryHeader(int entrySize,
                                      int documentFeatures,
                                      long combinedId,
                                      long documentMeta) {

    public IndexJournalEntryHeader(EdgeId<EdgeDomain> domainId,
                                   int documentFeatures,
                                   EdgeId<EdgeUrl> urlId,
                                   long documentMeta) {
        this(-1,
                documentFeatures,
                combineIds(domainId, urlId),
                documentMeta);
    }

    static long combineIds(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId) {
        long did = domainId.id();
        long uid = urlId.id();

        return (did << 32L) | uid;
    }

}
