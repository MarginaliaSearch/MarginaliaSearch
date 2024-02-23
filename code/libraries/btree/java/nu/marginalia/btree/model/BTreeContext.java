package nu.marginalia.btree.model;

import nu.marginalia.btree.BTreeWriter;

/** Specifies the parameters of a BTree. */
public class BTreeContext {
    public final int maxLayers;
    public final int entrySize;
    private final int blockSizeBits;
    private final int pageSize;

    // Below this number of data pages, a b-tree will not be constructed.
    //
    // 8 pages is the breaking point where using a B-tree is actually advantageous
    // over just binary searching in a sorted list. Above 8 pages, binary search will
    // worst-case four page faults. A b-tree will incur three page faults up until
    // ~100k-200k entries with typical configurations.
    private static final int MIN_PAGES_FOR_BTREE = 8;

    /**
     * @param maxLayers         The maximum number of index layers
     * @param entrySize         The entry size, for size 1 the key is the data. For sizes larger than 1,
     *                          the data will be expected to sit in the successive position to the key
     *                          in the data layer
     * @param blockSize         Specifies the size of each index layer. The data layers' size will be entrySize times
     *                          the blockSize. For on-disk BTrees ideal is anywhere below 4096b data size.
     *                          When testing the BTree you probably want as small a value as you can get away
     *                          with to reduce the need for RAM.
     *
     */
    public BTreeContext(int maxLayers, int entrySize, BTreeBlockSize blockSize) {
        this.maxLayers = maxLayers;
        this.entrySize = entrySize;
        this.blockSizeBits = blockSize.blockSizeBits;
        this.pageSize = 1 << blockSizeBits;
    }

    public long calculateSize(int numEntries) {
        var header = BTreeWriter.makeHeader(this, 0, numEntries);

        return header.dataOffsetLongs() + (long) numEntries * entrySize + 4;
    }

    public int numIndexLayers(int numEntries) {
        if (entrySize * numEntries <= pageSize * MIN_PAGES_FOR_BTREE) {
            return 0;
        }
        for (int i = 1; i < maxLayers; i++) {
            long div = (1L << (blockSizeBits *i));
            long frq = numEntries / div;
            if (frq < (1L << blockSizeBits)) {
                if (numEntries == (numEntries & div)) {
                    return i;
                }
                return i+1;
            }
        }
        return maxLayers;
    }

    public long indexLayerSize(int numWords, int level) {
        final long layerSize = 1L<<(blockSizeBits *(level+1));

        return pageSize * (numWords / layerSize + Long.signum(numWords % layerSize));
    }

    public int pageSize() {
        return pageSize;
    }

}
