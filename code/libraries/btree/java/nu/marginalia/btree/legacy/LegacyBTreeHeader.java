package nu.marginalia.btree.legacy;

import nu.marginalia.array.LongArray;


/**
 * The header of a legacy BTree segment.
 *
 * @param layers The number of index layers in the BTree
 * @param numEntries The number of entries in the BTree
 * @param indexOffsetLongs The offset of the index data in longs from the start of the BTree
 * @param dataOffsetLongs The offset of the data in longs from the start of the BTree
 */
public record LegacyBTreeHeader(int layers,
                          int numEntries,
                          long indexOffsetLongs,
                          long dataOffsetLongs) {

    public LegacyBTreeHeader {
        assert (layers >= 0);
        assert (numEntries >= 0);
        assert (indexOffsetLongs >= 0);
        assert (dataOffsetLongs >= 0);
        assert (dataOffsetLongs >= indexOffsetLongs);
    }

    public static final int BTreeHeaderSizeLongs = 3;

    public LegacyBTreeHeader(long a, long b, long c) {
        this((int)(a >>> 32), (int)(a & 0xFFFF_FFFFL), b, c);
    }
    public LegacyBTreeHeader(LongArray array, long offset) {
        this(array.get(offset), array.get(offset+1), array.get(offset+2));
    }

    public long[] getRelativeLayerOffsets(LegacyBTreeContext ctx) {
        long[] layerOffsets = new long[layers()];
        for (int i = 0; i < layers(); i++) {
            layerOffsets[i] = relativeIndexLayerOffset(ctx, i);
        }
        return layerOffsets;
    }

    public long relativeIndexLayerOffset(LegacyBTreeContext ctx, int n) {
        long offset = 0;

        for (int i = n+1; i < layers; i++) {
            offset += ctx.indexLayerSize( numEntries, i);
        }

        return offset;
    }

}
