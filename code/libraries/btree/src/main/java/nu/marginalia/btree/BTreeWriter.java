package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.delegate.ShiftedLongArray;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.btree.model.BTreeHeader;

import java.io.IOException;


public class BTreeWriter {
    private final BTreeContext ctx;
    private final LongArray map;

    public BTreeWriter(LongArray map, BTreeContext ctx) {
        this.map = map;
        this.ctx = ctx;
    }

    /** Construct a BTree with numEntries entries at offset in the associated map
     *
     * @return The size of the written data
     */
    public long write(long offset, int numEntries, BTreeWriteCallback writeIndexCallback)
            throws IOException
    {
        BTreeHeader header = makeHeader(offset, numEntries);

        writeHeader(header, map, offset);

        final long startRange = header.dataOffsetLongs();
        final long endRange = startRange + (long) numEntries * ctx.entrySize;

        var slice = map.range(startRange, endRange);

        final BTreeDogEar dogEar = createDogEar(ctx, header, slice);

        writeIndexCallback.write(slice);

        if (!dogEar.verify()) {
            throw new IllegalStateException("Dog ear was not overwritten: " + header);
        }

        assert slice.isSortedN(ctx.entrySize, 0, (long) numEntries * ctx.entrySize) : "Provided data was not sorted";

        if (header.layers() >= 1) { // Omit layer if data fits within a single block
            writeIndex(header);
        }

        return ctx.calculateSize(numEntries);
    }

    private void writeHeader(BTreeHeader header, LongArray map, long offset) {
        map.set(offset, ((long) header.layers() << 32L) | ((long)header.numEntries() & 0xFFFF_FFFFL));
        map.set(offset+1, header.indexOffsetLongs());
        map.set(offset+2, header.dataOffsetLongs());
    }

    private BTreeDogEar createDogEar(BTreeContext ctx, BTreeHeader header, LongArray slice) {
        if (BTreeWriter.class.desiredAssertionStatus()) {
            return BTreeDogEar.create(ctx, header, slice);
        }
        else {
            return BTreeDogEar.empty();
        }
    }

    public static BTreeHeader makeHeader(BTreeContext ctx, long baseOffset, int numEntries) {
        final int numLayers = ctx.numIndexLayers(numEntries);

        final long indexOffset = baseOffset
                + BTreeHeader.BTreeHeaderSizeLongs
                + headerPaddingSize(ctx, baseOffset, numLayers);
        final long dataOffset = indexOffset
                + indexSize(ctx, numEntries, numLayers);

        return new BTreeHeader(numLayers, numEntries, indexOffset, dataOffset);
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

    private static int headerPaddingSize(BTreeContext ctx, long baeOffset, int numLayers) {
        final int padding;
        if (numLayers == 0) {
            padding = 0;
        }
        else {
            /* If this the amount of data is big enough to be a b-tree and not just
             * a sorted list, there needs to be padding between the header and the index
             * in order to get aligned blocks
             */
            padding = (int) (ctx.pageSize() - ((baeOffset + BTreeHeader.BTreeHeaderSizeLongs) % ctx.pageSize()));
        }
        return padding;
    }

    public BTreeHeader makeHeader(long offset, int numEntries) {
        return makeHeader(ctx, offset, numEntries);
    }


    private void writeIndex(BTreeHeader header) {
        var layerOffsets = header.getRelativeLayerOffsets(ctx);

        long indexedDataStepSize = ctx.pageSize();

        /*  Index layer 0 indexes the data itself
            Index layer 1 indexes layer 0
            Index layer 2 indexes layer 1
            And so on
         */
        for (int layer = 0; layer < header.layers(); layer++,
                indexedDataStepSize*=ctx.pageSize()) {

            writeIndexLayer(header, layerOffsets, indexedDataStepSize, layer);
        }

    }

    private void writeIndexLayer(BTreeHeader header,
                                 long[] layerOffsets,
                                 final long indexedDataStepSize,
                                 final int layer) {

        final long indexOffsetBase = layerOffsets[layer] + header.indexOffsetLongs();
        final long dataOffsetBase = header.dataOffsetLongs();

        final long dataEntriesMax = header.numEntries();
        final int entrySize = ctx.entrySize;

        final long lastDataEntryOffset = indexedDataStepSize - 1;

        long indexWord = 0;

        for (long dataPtr = 0;
             dataPtr + lastDataEntryOffset < dataEntriesMax;
             dataPtr += indexedDataStepSize)
        {
            long dataOffset = dataOffsetBase + (dataPtr + lastDataEntryOffset) * entrySize;
            map.set(indexOffsetBase + indexWord++, map.get(dataOffset));
        }

            // If the index block is not completely filled with data,
            // top up the remaining index block with LONG_MAX

            final long trailerStart = indexOffsetBase + indexWord;
            final long trailerEnd = trailerStart
                    + ctx.pageSize()
                    - (int) (indexWord % ctx.pageSize());

            if (trailerStart < trailerEnd) {
                map.fill(trailerStart, trailerEnd, Long.MAX_VALUE);
            }
    }


}
