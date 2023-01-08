package nu.marginalia.util.btree.model;

import nu.marginalia.util.btree.BTreeWriter;

public record BTreeContext(int MAX_LAYERS,
                           int entrySize,
                           int BLOCK_SIZE_BITS,
                           int BLOCK_SIZE_WORDS) {

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
        if (numEntries <= BLOCK_SIZE_WORDS*MIN_PAGES_FOR_BTREE/entrySize) {
            return 0;
        }
        for (int i = 1; i < MAX_LAYERS; i++) {
            long div = (1L << (BLOCK_SIZE_BITS*i));
            long frq = numEntries / div;
            if (frq < (1L << BLOCK_SIZE_BITS)) {
                if (numEntries == (numEntries & div)) {
                    return i;
                }
                return i+1;
            }
        }
        return MAX_LAYERS;
    }

    public long indexLayerSize(int numWords, int level) {
        final long layerSize = 1L<<(BLOCK_SIZE_BITS*(level+1));

        return BLOCK_SIZE_WORDS * (numWords / layerSize + Long.signum(numWords % layerSize));
    }

}
