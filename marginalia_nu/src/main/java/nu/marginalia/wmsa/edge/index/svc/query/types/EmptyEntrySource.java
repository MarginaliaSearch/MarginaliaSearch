package nu.marginalia.wmsa.edge.index.svc.query.types;

import nu.marginalia.util.btree.BTreeQueryBuffer;

public class EmptyEntrySource implements EntrySource {
    @Override
    public void skip(int n) {
    }

    @Override
    public void read(BTreeQueryBuffer buffer) {
        buffer.zero();
    }

    @Override
    public boolean hasMore() {
        return false;
    }
}
