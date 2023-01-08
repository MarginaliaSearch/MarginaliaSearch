package nu.marginalia.wmsa.edge.index.query;

import nu.marginalia.util.array.buffer.LongQueryBuffer;

public interface EntrySource {
    void skip(int n);
    void read(LongQueryBuffer buffer);

    boolean hasMore();
}
