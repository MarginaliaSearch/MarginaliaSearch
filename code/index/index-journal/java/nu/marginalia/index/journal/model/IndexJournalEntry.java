package nu.marginalia.index.journal.model;

import nu.marginalia.model.id.UrlIdCodec;

/** An entry in the index journal.
 *
 * @param header the header of the entry, containing document level data
 * @param data the data of the entry, containing keyword level data
 *
 * @see IndexJournalEntryHeader
 * @see IndexJournalEntryData
 */
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
