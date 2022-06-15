package nu.marginalia.util.btree;

import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class BTreeWriter {
    private final Logger logger = LoggerFactory.getLogger(BTreeWriter.class);
    private final BTreeContext ctx;
    private final MultimapFileLong map;

    public BTreeWriter(MultimapFileLong map, BTreeContext ctx) {
        this.map = map;
        this.ctx = ctx;
    }

    private static long indexSize(BTreeContext ctx, int numWords, int numLayers) {
        if (numLayers == 0) {
            return 0; // Special treatment for small tables
        }

        long size = 0;
        for (int layer = 0; layer < numLayers; layer++) {
            size += ctx.layerSize(numWords, layer);
        }
        return size;
    }

    public long write(long offset, int numEntries, WriteCallback writeIndex)
            throws IOException
    {
        var header = makeHeader(offset, numEntries);

        header.write(map, offset);
        writeIndex.write(header.dataOffsetLongs());

        if (header.layers() < 1) {
            return ctx.calculateSize(numEntries);
        }

        writeIndex(header);

        return ctx.calculateSize(numEntries);
    }

    public static BTreeHeader makeHeader(BTreeContext ctx, long offset, int numEntries) {
        final int numLayers = ctx.numLayers(numEntries);

        final int padding = BTreeHeader.getPadding(ctx, offset, numLayers);

        final long indexOffset = offset + BTreeHeader.BTreeHeaderSizeLongs + padding;
        final long dataOffset = indexOffset + indexSize(ctx, numEntries, numLayers);

        return new BTreeHeader(numLayers, numEntries, indexOffset, dataOffset);
    }

    public BTreeHeader makeHeader(long offset, int numEntries) {
        return makeHeader(ctx, offset, numEntries);
    }


    private void writeIndex(BTreeHeader header) {
        var layerOffsets = getRelativeLayerOffsets(header);

        long stride = ctx.BLOCK_SIZE_WORDS();
        for (int layer = 0; layer < header.layers(); layer++,
                stride*=ctx.BLOCK_SIZE_WORDS()) {
            long indexWord = 0;
            long offsetBase = layerOffsets[layer] + header.indexOffsetLongs();
            long numEntries = header.numEntries();
            for (long idx = 0; idx < numEntries; idx += stride, indexWord++) {
                long dataOffset = header.dataOffsetLongs() + (idx + (stride-1)) * ctx.entrySize();
                long val;

                if (idx + (stride-1) < numEntries) {
                    val = map.get(dataOffset) & ctx.equalityMask();
                }
                else {
                    val = Long.MAX_VALUE;
                }
                if (offsetBase + indexWord < 0) {
                    logger.error("bad put @ {}", offsetBase + indexWord);
                    logger.error("layer{}", layer);
                    logger.error("layer offsets {}", layerOffsets);
                    logger.error("offsetBase = {}", offsetBase);
                    logger.error("numEntries = {}", numEntries);
                    logger.error("indexWord = {}", indexWord);
                }
                map.put(offsetBase + indexWord, val);
            }
            for (; (indexWord % ctx.BLOCK_SIZE_WORDS()) != 0; indexWord++) {
                map.put(offsetBase + indexWord, Long.MAX_VALUE);
            }
        }

    }

    private long[] getRelativeLayerOffsets(BTreeHeader header) {
        long[] layerOffsets = new long[header.layers()];
        for (int i = 0; i < header.layers(); i++) {
            layerOffsets[i] = header.relativeLayerOffset(ctx, i);
        }
        return layerOffsets;
    }
}
