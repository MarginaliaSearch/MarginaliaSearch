package nu.marginalia.index.reverse;

import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;

class ReverseIndexParameters {
    public static final int ENTRY_SIZE = 2;

    // This is the byte size per index page on disk, the data pages are twice as large due to ENTRY_SIZE = 2.
    //
    // Given a hardware limit of 4k reads, 2k block size should be optimal.
    public static final BTreeBlockSize blockSize = BTreeBlockSize.BS_2048;


    public static final BTreeContext bTreeContext = new BTreeContext(5, ENTRY_SIZE, blockSize);
}
