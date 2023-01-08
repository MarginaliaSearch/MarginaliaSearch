package nu.marginalia.wmsa.edge.index.postings.reverse;

import nu.marginalia.util.btree.model.BTreeContext;

class ReverseIndexParameters {
    public static final int ENTRY_SIZE = 2;

    public static final BTreeContext bTreeContext = new BTreeContext(5, ENTRY_SIZE, 8);
}
