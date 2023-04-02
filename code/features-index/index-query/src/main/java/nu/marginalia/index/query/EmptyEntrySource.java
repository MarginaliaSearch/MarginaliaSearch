package nu.marginalia.index.query;

import nu.marginalia.array.buffer.LongQueryBuffer;

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
