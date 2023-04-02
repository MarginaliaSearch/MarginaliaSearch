package nu.marginalia.index.query;

import nu.marginalia.array.buffer.LongQueryBuffer;

public interface EntrySource {
    void skip(int n);
    void read(LongQueryBuffer buffer);

    boolean hasMore();

    String indexName();
}
