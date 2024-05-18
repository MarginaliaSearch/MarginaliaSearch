package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
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
        BTreeHeader header = makeHeader(ctx, offset, numEntries);

        // Write the header
        map.set(offset, ((long) header.layers() << 32L) | ((long)header.numEntries() & 0xFFFF_FFFFL));
        map.set(offset+1, header.indexOffsetLongs());
        map.set(offset+2, header.dataOffsetLongs());

        // Calculate the data range
        final long startRange = header.dataOffsetLongs();
        final long endRange = startRange + (long) numEntries * ctx.entrySize;

        // Prepare to write the data
        var slice = map.range(startRange, endRange);

        final BTreeDogEar dogEar = createDogEar(ctx, header, slice);

        writeIndexCallback.write(slice);

        // Sanity checks to ensure the b-tree is written correctly
        if (!dogEar.verify()) {
            throw new IllegalStateException("Dog ear was not overwritten: " + header);
        }
        assert slice.isSortedN(ctx.entrySize, 0, (long) numEntries * ctx.entrySize) : "Provided data was not sorted";

        // Write the index if there is enough data to warrant it
        if (header.layers() >= 1) {
            writeIndex(header);
        }

        // Return the size of the written data
        return endRange - offset;
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

        // Calculate the offset for the index relative to the header start
        long indexOffset = baseOffset + BTreeHeader.BTreeHeaderSizeLongs;

        if (numLayers > 0) {
            // Align the index to the next page
            indexOffset += (int) (ctx.pageSize() - ((baseOffset + BTreeHeader.BTreeHeaderSizeLongs) % ctx.pageSize()));
        }

        // Calculate the offset for the data relative to the header start
        long dataOffset = indexOffset;
        for (int layer = 0; layer < numLayers; layer++) {
            dataOffset += ctx.indexLayerSize(numEntries, layer);
        }

        return new BTreeHeader(numLayers, numEntries, indexOffset, dataOffset);
    }

    private void writeIndex(BTreeHeader header) {
        long indexedDataStepSize = ctx.pageSize();

        /*  Index layer 0 indexes the data itself
            Index layer 1 indexes layer 0
            Index layer 2 indexes layer 1
            And so on
         */
        for (int layer = 0; layer < header.layers(); layer++,
                indexedDataStepSize*=ctx.pageSize()) {
            writeIndexLayer(header, indexedDataStepSize, layer);
        }

    }

    /** Write an index layer
     *
     * @param header The header of the BTree
     * @param stepSize The step size of the indexed data
     * @param layer The layer to write
     */
    private void writeIndexLayer(BTreeHeader header,
                                 final long stepSize,
                                 final int layer) {

        final long layerStart = header.indexOffsetLongs() + header.relativeIndexLayerOffset(ctx, layer);
        final long dataStart = header.dataOffsetLongs();

        long writeOffset = 0;

        // Write the index layer

        // Each index layer implicitly indexes the data layer below it,
        // so that for the data segment corresponding to each index value,
        // has values that are smaller than or equal to the index value.

        // Thus to construct the index, we take the last value of each data segment
        // and write it to the index layer

        for (long readOffset = 0;
             readOffset + stepSize <= header.numEntries();
             readOffset += stepSize)
        {
            final long dest = layerStart + writeOffset++;

            final long src = dataStart
                    + readOffset * ctx.entrySize      // relative offset of the data range
                    + (stepSize - 1) * ctx.entrySize; // relative offset of the last entry in the data range

            map.set(dest, map.get(src));
        }

        // If the index block is not completely filled with data,
        // top up the remaining index block with LONG_MAX as we require
        // the index to be fully populated and sorted for the binary search
        // to work

        final long trailerStart = layerStart + writeOffset;
        final long trailerEnd = trailerStart
                + ctx.pageSize()
                - (int) (writeOffset % ctx.pageSize());
        if (trailerStart < trailerEnd) {
            map.fill(trailerStart, trailerEnd, Long.MAX_VALUE);
        }
    }


}
