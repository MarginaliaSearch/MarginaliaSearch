package nu.marginalia.index.query;

import nu.marginalia.array.page.LongQueryBuffer;

/** Dummy EntrySource that returns no entries. */
public class EmptyEntrySource implements EntrySource {
    @Override
    public void skip(int n) {
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
        return "Empty";
    }
}
