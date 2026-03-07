package nu.marginalia.btree.legacy;

import nu.marginalia.array.LongArray;

import java.io.IOException;


public class LegacyBTreeWriter {
    private final LegacyBTreeContext ctx;
    private final LongArray map;

    public LegacyBTreeWriter(LongArray map, LegacyBTreeContext ctx) {
        this.map = map;
        this.ctx = ctx;
    }

    /** Construct a BTree with numEntries entries at offset in the associated map
     *
     * @return The size of the written data
     */
    public long write(long offset, int numEntries, LegacyBTreeWriteCallback writeIndexCallback)
            throws IOException
    {
        LegacyBTreeHeader header = makeHeader(ctx, offset, numEntries);

        // Write the header
        map.set(offset, ((long) header.layers() << 32L) | ((long)header.numEntries() & 0xFFFF_FFFFL));
        map.set(offset+1, header.indexOffsetLongs());
        map.set(offset+2, header.dataOffsetLongs());

        // Calculate the data range
        final long startRange = header.dataOffsetLongs();
        final long endRange;
        if (header.layers() == 0) {
            endRange = offset + ctx.pageSize();
            assert ctx.pageSize() - 3 >= numEntries * ctx.entrySize;
        }
        else {
            long dataSizeLongs = (long) numEntries * ctx.entrySize;
            long dataSizeBlockRounded = (long) ctx.pageSize() * ( dataSizeLongs / ctx.pageSize() + Long.signum(dataSizeLongs % ctx.pageSize()));
            endRange = startRange + dataSizeBlockRounded;
        }

        // Prepare to write the data
        var slice = map.range(startRange, endRange);

        final LegacyBTreeDogEar dogEar = createDogEar(ctx, header, slice);

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
        long size = endRange - offset;
        assert (size % ctx.pageSize()) == 0 : "Size is not page size aligned, was " + size + ", page size = " + ctx.pageSize();
        return size;
    }


    private LegacyBTreeDogEar createDogEar(LegacyBTreeContext ctx, LegacyBTreeHeader header, LongArray slice) {
        if (LegacyBTreeWriter.class.desiredAssertionStatus()) {
            return LegacyBTreeDogEar.create(ctx, header, slice);
        }
        else {
            return LegacyBTreeDogEar.empty();
        }
    }

    public static LegacyBTreeHeader makeHeader(LegacyBTreeContext ctx, long baseOffset, int numEntries) {
        final int numLayers = ctx.numIndexLayers(numEntries);

        // Calculate the offset for the index relative to the header start
        long indexOffset = baseOffset + LegacyBTreeHeader.BTreeHeaderSizeLongs;

        if (numLayers > 0) {
            // Align the index to the next page
            indexOffset += (int) (ctx.pageSize() - ((baseOffset + LegacyBTreeHeader.BTreeHeaderSizeLongs) % ctx.pageSize()));
        }

        // Calculate the offset for the data relative to the header start
        long dataOffset = indexOffset;
        for (int layer = 0; layer < numLayers; layer++) {
            dataOffset += ctx.indexLayerSize(numEntries, layer);
        }

        return new LegacyBTreeHeader(numLayers, numEntries, indexOffset, dataOffset);
    }

    private void writeIndex(LegacyBTreeHeader header) {
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
    private void writeIndexLayer(LegacyBTreeHeader header,
                                 final long stepSize,
                                 final int layer) {

        final long layerStart = header.indexOffsetLongs() + header.relativeIndexLayerOffset(ctx, layer);
        final long dataStart = header.dataOffsetLongs();

        long writeOffset = 0;

        for (long readOffset = 0;
             readOffset + stepSize <= header.numEntries();
             readOffset += stepSize)
        {
            final long dest = layerStart + writeOffset++;

            final long src = dataStart
                    + readOffset * ctx.entrySize
                    + (stepSize - 1) * ctx.entrySize;

            map.set(dest, map.get(src));
        }

        final long trailerStart = layerStart + writeOffset;
        final long trailerEnd = trailerStart
                + ctx.pageSize()
                - (int) (writeOffset % ctx.pageSize());
        if (trailerStart < trailerEnd) {
            map.fill(trailerStart, trailerEnd, Long.MAX_VALUE);
        }
    }


}
