package nu.marginalia.wmsa.edge.index.reader.query.types;

import nu.marginalia.wmsa.edge.index.reader.SearchIndex;

public interface EntrySource {
    SearchIndex getIndex();
    int read(long[] buffer, int n);
}
