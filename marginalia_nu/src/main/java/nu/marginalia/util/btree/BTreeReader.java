package nu.marginalia.util.btree;

import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BTreeReader {

    private final MultimapFileLong file;
    private final BTreeContext ctx;

    private final Logger logger = LoggerFactory.getLogger(BTreeReader.class);

    private final MultimapSearcher indexSearcher;
    private final MultimapSearcher dataSearcher;

    public BTreeReader(MultimapFileLong file, BTreeContext ctx) {
        this.file = file;
        this.indexSearcher = MultimapSearcher.forContext(file, ~0, 1);
        this.dataSearcher = MultimapSearcher.forContext(file, ctx.equalityMask(), ctx.entrySize());

        this.ctx = ctx;
    }

    public BTreeHeader getHeader(long fileOffset) {
        return new BTreeHeader(file.get(fileOffset), file.get(fileOffset+1), file.get(fileOffset+2));
    }

    /**
     *
     * @return file offset of entry matching keyRaw, negative if absent
     */
    public long findEntry(BTreeHeader header, final long keyRaw) {
        final long key = keyRaw & ctx.equalityMask();

        final long dataAddress = header.dataOffsetLongs();
        final int entrySize = ctx.entrySize();
        final int blockSize = ctx.BLOCK_SIZE_WORDS();

        if (header.layers() == 0) { // For small data, we only have a data block
            return dataSearcher.binarySearchUpperBound(key, dataAddress, header.numEntries());
        }

        final long indexOffset = header.indexOffsetLongs();

        // Search the top layer
        long layerOffset = indexSearch(key, indexOffset, blockSize);
        if (layerOffset < 0) return -1;

        // Search intermediary layers
        for (int i = header.layers() - 2; i >= 0; --i) {
            final long layerAddressBase = indexOffset + header.relativeIndexLayerOffset(ctx, i);
            final long layerBlockOffset = layerAddressBase + layerOffset;

            final long nextLayerOffset = indexSearch(key, layerBlockOffset, blockSize);
            if (nextLayerOffset < 0)
                return -1;

            layerOffset = blockSize*(nextLayerOffset + layerOffset);
        }

        // Search the corresponding data block
        final long searchStart = dataAddress + layerOffset * entrySize;
        final long lastDataAddress = dataAddress + (long) header.numEntries() * entrySize;
        final long lastItemInBlockAddress = searchStart + (long) blockSize * entrySize;
        final long searchEnd = Math.min(lastItemInBlockAddress, lastDataAddress);

        return dataSearcher.binarySearchUpperBound(key, searchStart, (searchEnd - searchStart) / entrySize);
    }

    private long indexSearch(long key, long start, long n) {
        return indexSearcher.binarySearch(key, start, n) - start;
    }

}
