package nu.marginalia.btree.model;

import nu.marginalia.btree.BTreeWriter;

public record BTreeContext(int maxLayers,
                           int entrySize,
                           int blockSizeBits,
                           int blockSizeWords) {

    // 8 pages is the breaking point where using a B-tree is actually advantageous
    // over just binary searching in a sorted list. Above 8 pages, binary search will
    // worst-case four page faults. A b-tree will incur three page faults up until
    // ~100k-200k entries with typical configurations.

    private static final int MIN_PAGES_FOR_BTREE = 8;

    public BTreeContext(int MAX_LAYERS, int entrySize, int BLOCK_SIZE_BITS) {
        this(MAX_LAYERS, entrySize, BLOCK_SIZE_BITS, 1 << BLOCK_SIZE_BITS);
    }

    public long calculateSize(int numEntries) {
        var header = BTreeWriter.makeHeader(this, 0, numEntries);

        return header.dataOffsetLongs() + (long) numEntries * entrySize + 4;
    }

    public int numIndexLayers(int numEntries) {
        if (numEntries <= blockSizeWords *MIN_PAGES_FOR_BTREE/entrySize) {
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

        return blockSizeWords * (numWords / layerSize + Long.signum(numWords % layerSize));
    }

}
