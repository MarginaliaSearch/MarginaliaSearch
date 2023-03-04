package nu.marginalia.index.journal.model;

import gnu.trove.list.array.TLongArrayList;

public class IndexJournalEntryBuilder {
    private final long documentId;
    private final long documentMeta;
    private final TLongArrayList items = new TLongArrayList();

    public IndexJournalEntryBuilder(long documentId, long documentMeta) {
        this.documentId = documentId;
        this.documentMeta = documentMeta;
    }

    public IndexJournalEntryBuilder capacity(int size) {
        items.ensureCapacity(size);
        return this;
    }

    public IndexJournalEntryBuilder add(long wordId, long metadata) {

        items.add(wordId);
        items.add(metadata);

        return this;
    }

    public IndexJournalEntry build() {
        return new IndexJournalEntry(
                new IndexJournalEntryHeader(items.size(), documentId, documentMeta),
                new IndexJournalEntryData(items.toArray())
        );
    }
}
