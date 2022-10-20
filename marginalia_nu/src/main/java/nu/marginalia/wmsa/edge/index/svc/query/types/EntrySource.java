package nu.marginalia.wmsa.edge.index.svc.query.types;

import nu.marginalia.util.btree.BTreeQueryBuffer;

public interface EntrySource {
    void skip(int n);
    void read(BTreeQueryBuffer buffer);

    boolean hasMore();
}
