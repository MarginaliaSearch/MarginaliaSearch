package nu.marginalia.wmsa.edge.index.postings.journal.writer;

import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntryHeader;

public interface SearchIndexJournalWriter {
    void put(SearchIndexJournalEntryHeader header, SearchIndexJournalEntry entry);

    void forceWrite();

    void flushWords();

}
