package nu.marginalia.wmsa.edge.index.journal;

import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.journal.model.SearchIndexJournalEntryHeader;

public interface SearchIndexJournalWriter {
    void put(SearchIndexJournalEntryHeader header, SearchIndexJournalEntry entry);

    void forceWrite();

    void flushWords();

}
