package nu.marginalia.index.reverse.query;

import nu.marginalia.array.page.LongQueryBuffer;

/** Dummy EntrySource that returns no entries. */
public class EmptyEntrySource implements EntrySource {

    private final String attemptedIndex;
    public final String requestedTerm;

    public EmptyEntrySource(String attemptedIndex, String requestedTerm) {
        this.attemptedIndex = attemptedIndex;
        this.requestedTerm = requestedTerm;
    }

    @Override
    public void read(LongQueryBuffer buffer) {
        buffer.zero();
    }

    @Override
    public boolean hasMore() {
        return false;
    }


    @Override
    public String indexName() {
        return "Empty: " + attemptedIndex + " / " + requestedTerm;
    }

    @Override
    public int readEntries() {
        return 0;
    }
}
