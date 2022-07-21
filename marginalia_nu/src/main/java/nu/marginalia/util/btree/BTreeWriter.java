package nu.marginalia.util.btree;

import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLongSlice;

import java.io.IOException;


public class BTreeWriter {
    private final BTreeContext ctx;
    private final MultimapFileLongSlice map;

    public BTreeWriter(MultimapFileLongSlice map, BTreeContext ctx) {
        this.map = map;
        this.ctx = ctx;
    }

    private static long indexSize(BTreeContext ctx, int numWords, int numLayers) {
        if (numLayers == 0) {
            return 0; // Special treatment for small tables
        }

        long size = 0;
        for (int layer = 0; layer < numLayers; layer++) {
            size += ctx.indexLayerSize(numWords, layer);
        }
        return size;
    }

    /** Construct a BTree with numEntries entries at offset in the associated map
     *
     * @return The size of the written data
     */
    public long write(long offset, int numEntries, WriteCallback writeIndexCallback)
            throws IOException
    {
        BTreeHeader header = makeHeader(offset, numEntries);

        header.write(map, offset);

        writeIndexCallback.write(map.atOffset(header.dataOffsetLongs()));

        if (header.layers() < 1) { // The data is too small to benefit from indexing
            return ctx.calculateSize(numEntries);
        }
        else {
            writeIndex(header);
            return ctx.calculateSize(numEntries);
        }
    }

    public static BTreeHeader makeHeader(BTreeContext ctx, long offset, int numEntries) {
        final int numLayers = ctx.numIndexLayers(numEntries);

        final int padding = BTreeHeader.getPadding(ctx, offset, numLayers);

        final long indexOffset = offset + BTreeHeader.BTreeHeaderSizeLongs + padding;
        final long dataOffset = indexOffset + indexSize(ctx, numEntries, numLayers);

        return new BTreeHeader(numLayers, numEntries, indexOffset, dataOffset);
    }

    public BTreeHeader makeHeader(long offset, int numEntries) {
        return makeHeader(ctx, offset, numEntries);
    }


    private void writeIndex(BTreeHeader header) {
        var layerOffsets = header.getRelativeLayerOffsets(ctx);

        long indexedDataStepSize = ctx.BLOCK_SIZE_WORDS();

        /*  Index layer 0 indexes the data itself
            Index layer 1 indexes layer 0
            Index layer 2 indexes layer 1
            And so on
         */
        for (int layer = 0; layer < header.layers(); layer++,
                indexedDataStepSize*=ctx.BLOCK_SIZE_WORDS()) {

            writeIndexLayer(header, layerOffsets, indexedDataStepSize, layer);
        }

    }

    private void writeIndexLayer(BTreeHeader header, long[] layerOffsets,
                                 final long indexedDataStepSize,
                                 final int layer) {

        final long indexOffsetBase = layerOffsets[layer] + header.indexOffsetLongs();
        final long dataOffsetBase = header.dataOffsetLongs();

        final long dataEntriesMax = header.numEntries();
        final int entrySize = ctx.entrySize();

        final long lastDataEntryOffset = indexedDataStepSize - 1;

        long indexWord = 0;

        for (long dataPtr = 0;
             dataPtr + lastDataEntryOffset < dataEntriesMax;
             dataPtr += indexedDataStepSize)
        {
            long dataOffset = dataOffsetBase + (dataPtr + lastDataEntryOffset) * entrySize;
            map.put(indexOffsetBase + indexWord++, map.get(dataOffset) & ctx.equalityMask());
        }

        // Fill the remaining block with LONG_MAX
        map.setRange(indexOffsetBase+indexWord,
                (int) (ctx.BLOCK_SIZE_WORDS() - (indexWord % ctx.BLOCK_SIZE_WORDS())),
                Long.MAX_VALUE);
    }


}
