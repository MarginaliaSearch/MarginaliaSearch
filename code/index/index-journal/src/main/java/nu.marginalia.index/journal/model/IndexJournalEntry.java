package nu.marginalia.index.journal.model;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;

public record IndexJournalEntry(IndexJournalEntryHeader header, IndexJournalEntryData data) {

    public static IndexJournalEntryBuilder builder(long documentId, long documentMeta) {
        return new IndexJournalEntryBuilder(documentId, documentMeta);
    }

    public static IndexJournalEntryBuilder builder(int domainId,
                                                   int urlId,
                                                   long documentMeta) {


        return builder(new EdgeId<>(domainId), new EdgeId<>(urlId), documentMeta);
    }

    public static IndexJournalEntryBuilder builder(EdgeId<EdgeDomain> domainId,
                                                   EdgeId<EdgeUrl> urlId,
                                                   long documentMeta) {


        return new IndexJournalEntryBuilder(IndexJournalEntryHeader.combineIds(domainId, urlId), documentMeta);
    }
}
