package nu.marginalia.index.journal.model;

public record IndexJournalEntryHeader(int entrySize,
                                      int documentFeatures,
                                      long combinedId,
                                      long documentMeta) {

    public IndexJournalEntryHeader(long combinedId,
                                   int documentFeatures,
                                   long documentMeta) {
        this(-1,
                documentFeatures,
                combinedId,
                documentMeta);
    }

}
