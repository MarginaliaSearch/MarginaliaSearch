package nu.marginalia.util.btree.model;

import nu.marginalia.util.btree.BTreeWriter;

public record BTreeContext(int MAX_LAYERS,
                           int entrySize,
                           long equalityMask,
                           int BLOCK_SIZE_BITS,
                           int BLOCK_SIZE_WORDS) {

    public BTreeContext(int MAX_LAYERS, int entrySize, long equalityMask, int BLOCK_SIZE_BITS) {
        this(MAX_LAYERS, entrySize, equalityMask, BLOCK_SIZE_BITS, 1 << BLOCK_SIZE_BITS);
    }

    public long calculateSize(int numEntries) {
        var header = BTreeWriter.makeHeader(this, 0, numEntries);

        return header.dataOffsetLongs() + (long)numEntries * entrySize;
    }

    public int numIndexLayers(int numEntries) {
        if (numEntries <= BLOCK_SIZE_WORDS*2/entrySize) {
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
        final long numBlocks = numWords / layerSize;

        if (numWords % layerSize != 0) {
            return BLOCK_SIZE_WORDS * (numBlocks + 1);
        }
        return BLOCK_SIZE_WORDS * numBlocks;
    }

}
