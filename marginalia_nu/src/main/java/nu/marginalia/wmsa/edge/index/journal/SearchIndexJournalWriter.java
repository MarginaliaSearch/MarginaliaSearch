package nu.marginalia.wmsa.edge.index.journal;

public interface SearchIndexJournalWriter {
    void put(SearchIndexJournalEntryHeader header, SearchIndexJournalEntry entry);

    void forceWrite();

    void flushWords();

}
