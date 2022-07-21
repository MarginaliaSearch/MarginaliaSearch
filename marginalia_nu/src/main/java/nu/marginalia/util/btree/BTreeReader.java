package nu.marginalia.util.btree;

import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapSearcher;

import static java.lang.Math.min;

public class BTreeReader {

    private final MultimapFileLong file;
    private final BTreeContext ctx;

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
        final int blockSize = ctx.BLOCK_SIZE_WORDS();

        final long key = keyRaw & ctx.equalityMask();
        final long dataAddress = header.dataOffsetLongs();

        final long searchStart;
        final long numEntries;

        if (header.layers() == 0) { // For small data, there is no index block, only a flat data block
            searchStart = dataAddress;
            numEntries = header.numEntries();
        }
        else {
            long dataLayerOffset = searchIndex(header, key);
            if (dataLayerOffset < 0) {
                return dataLayerOffset;
            }

            searchStart = dataAddress + dataLayerOffset * ctx.entrySize();
            numEntries = min(header.numEntries() - dataLayerOffset, blockSize);
        }

        return dataSearcher.binarySearch(key, searchStart, numEntries);
    }

    private long searchIndex(BTreeHeader header, long key) {
        final int blockSize = ctx.BLOCK_SIZE_WORDS();
        final long indexAddress = header.indexOffsetLongs();

        long layerOffset = 0;

        for (int i = header.layers() - 1; i >= 0; --i) {
            final long indexLayerBlockOffset = header.relativeIndexLayerOffset(ctx, i) + layerOffset;

            final long nextLayerOffset = relativePositionInIndex(key, indexAddress + indexLayerBlockOffset, blockSize);
            if (nextLayerOffset < 0)
                return nextLayerOffset;

            layerOffset = blockSize * (nextLayerOffset + layerOffset);
        }

        return layerOffset;
    }

    private long relativePositionInIndex(long key, long start, long n) {
        return indexSearcher.binarySearchUpper(key, start, n) - start;
    }

}
