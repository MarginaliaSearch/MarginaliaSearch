package nu.marginalia.util.btree.model;

import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapFileLongSlice;

public record BTreeHeader(int layers, int numEntries, long indexOffsetLongs, long dataOffsetLongs) {
    public BTreeHeader {
        assert (layers >= 0);
        assert (numEntries >= 0);
        assert (indexOffsetLongs >= 0);
        assert (dataOffsetLongs >= 0);
        assert (dataOffsetLongs >= indexOffsetLongs);
    }

    public static final int BTreeHeaderSizeLongs = 3;

    public BTreeHeader(long a, long b, long c) {
        this((int)(a >>> 32), (int)(a & 0xFFFF_FFFFL), b, c);
    }

    public static int getPadding(BTreeContext ctx, long offset, int numLayers) {
        final int padding;
        if (numLayers == 0) {
            padding = 0;
        }
        else {
            padding = (int) (ctx.BLOCK_SIZE_WORDS() - ((offset + BTreeHeader.BTreeHeaderSizeLongs) % ctx.BLOCK_SIZE_WORDS()));
        }
        return padding;
    }

    public void write(MultimapFileLongSlice dest, long offset) {
        dest.put(offset, ((long) layers << 32L) | ((long)numEntries & 0xFFFF_FFFFL));
        dest.put(offset+1, indexOffsetLongs);
        dest.put(offset+2, dataOffsetLongs);
    }


    public long relativeLayerOffset(BTreeContext ctx, int n) {
        long offset = 0;
        for (int i = n+1; i < layers; i++) {
            offset += ctx.layerSize( numEntries, i);
        }
        return offset;
    }

}
