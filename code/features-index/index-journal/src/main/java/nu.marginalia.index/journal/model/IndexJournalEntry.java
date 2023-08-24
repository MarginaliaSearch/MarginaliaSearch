package nu.marginalia.index.journal.model;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.EdgeId;
import nu.marginalia.model.id.UrlIdCodec;

public record IndexJournalEntry(IndexJournalEntryHeader header, IndexJournalEntryData data) {

    public static IndexJournalEntryBuilder builder(long documentId, long documentMeta) {
        return new IndexJournalEntryBuilder(0, documentId, documentMeta);
    }

    public static IndexJournalEntryBuilder builder(int domainId,
                                                   int urlId,
                                                   long documentMeta) {


        return builder(UrlIdCodec.encodeId(domainId, urlId), documentMeta);
    }

}
