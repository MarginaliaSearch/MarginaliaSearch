package nu.marginalia.index.journal.writer;

import nu.marginalia.index.journal.model.IndexJournalEntry;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;

import java.io.IOException;

public interface IndexJournalWriter {
    void put(IndexJournalEntryHeader header, IndexJournalEntryData entry);
    default void put(IndexJournalEntry entry) {
        put(entry.header(), entry.data());
    }

    void forceWrite() throws IOException;

    void flushWords();
    void close() throws IOException;

}
