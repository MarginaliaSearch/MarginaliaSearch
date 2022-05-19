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
    private final long mask;
    private final MultimapSearcher searcher;

    public BTreeReader(MultimapFileLong file, BTreeContext ctx) {
        this.file = file;
        this.searcher = file.createSearcher();
        this.ctx = ctx;
        this.mask = ctx.equalityMask();
    }

    public long fileSize() {
        return file.size();
    }

    public BTreeHeader getHeader(long offset) {
        return new BTreeHeader(file.get(offset), file.get(offset+1), file.get(offset+2));
    }

    public long offsetForEntry(BTreeHeader header, final long keyRaw) {
        final long key = keyRaw & mask;

        if (header.layers() == 0) {
            return trivialSearch(header, key);
        }

        long p = searchEntireTopLayer(header, key);
        if (p < 0) return -1;

        long cumOffset = p * ctx.BLOCK_SIZE_WORDS();
        for (int i = header.layers() - 2; i >= 0; --i) {
            long offsetBase = header.indexOffsetLongs() + header.relativeLayerOffset(ctx, i);
            p = searchLayerBlock(key, offsetBase+cumOffset);
            if (p < 0)
                return -1;
            cumOffset = ctx.BLOCK_SIZE_WORDS()*(p + cumOffset);
        }

        long dataMax = header.dataOffsetLongs() + (long) header.numEntries() * ctx.entrySize();
        return searchDataBlock(key,
                header.dataOffsetLongs() + ctx.entrySize()*cumOffset,
                dataMax);
    }


    private long searchEntireTopLayer(BTreeHeader header, long key) {
        long offset = header.indexOffsetLongs();

        return searcher.binarySearchUpperBound(key, offset, offset + ctx.BLOCK_SIZE_WORDS()) - offset;
    }

    private long searchLayerBlock(long key, long blockOffset) {
        if (blockOffset < 0)
            return blockOffset;

        return searcher.binarySearchUpperBound(key, blockOffset, blockOffset + ctx.BLOCK_SIZE_WORDS()) - blockOffset;
    }


    private long searchDataBlock(long key, long blockOffset, long dataMax) {
        if (blockOffset < 0)
            return blockOffset;

        long lastOffset = Math.min(blockOffset+ctx.BLOCK_SIZE_WORDS()*(long)ctx.entrySize(), dataMax);
        int length = (int)(lastOffset - blockOffset);

        if (ctx.entrySize() == 1) {
            if (mask == ~0L) return searcher.binarySearchUpperBoundNoMiss(key, blockOffset, blockOffset+length);
            return searcher.binarySearchUpperBoundNoMiss(key, blockOffset, blockOffset+length, mask);
        }

        return searcher.binarySearchUpperBoundNoMiss(key, blockOffset, ctx.entrySize(), length/ctx.entrySize(), mask);
    }

    private long trivialSearch(BTreeHeader header, long key) {
        long offset = header.dataOffsetLongs();

        if (ctx.entrySize() == 1) {
            if (mask == ~0L) {
                return searcher.binarySearchUpperBoundNoMiss(key, offset, offset+header.numEntries());
            }
            else {
                return searcher.binarySearchUpperBoundNoMiss(key, offset, offset+header.numEntries(), mask);
            }
        }

        return searcher.binarySearchUpperBoundNoMiss(key, offset, ctx.entrySize(), header.numEntries(), mask);

    }

}
