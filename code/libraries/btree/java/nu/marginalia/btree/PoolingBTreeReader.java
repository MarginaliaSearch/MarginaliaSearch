package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferEvictionPolicy;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.array.pool.BufferReadaheadPolicy;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.btree.model.BTreeHeader;
import org.slf4j.helpers.CheckReturnValue;

import static java.lang.Math.min;

public class PoolingBTreeReader {

    long indexStartOffset;
    long indexEndOffset;

    long dataStartOffset;
    long dataEndOffset;

    public final BTreeContext ctx;
    private final BTreeHeader header;

    private final long dataBlockEnd;
    private final BufferPool indexPool;
    private final BufferPool dataPool;

    public PoolingBTreeReader(BufferPool indexPool,
                              BufferPool dataPool,
                              BTreeContext ctx, long offset) {
        if ((offset % ctx.pageSize()) != 0) {
            throw new IllegalArgumentException("Offset must be a multiple of page size " + (ctx.pageSize()*8) + "bytes, was " + offset);
        }
        this.ctx = ctx;

        this.indexPool = indexPool;
        this.dataPool = dataPool;

        try (LongArray rootPage = indexPool.get(offset * 8, BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.NONE)) {
            this.header = new BTreeHeader(rootPage, 0);
        }
        if (header.numEntries() == 0) {
            throw new IllegalStateException("Num entries can not be 0, likely pointing to a zeroed memory area, reading at " + offset);
        }

        dataBlockEnd = (long) ctx.entrySize * header.numEntries();

        indexStartOffset = header.indexOffsetLongs();
        indexEndOffset = header.dataOffsetLongs();
        dataStartOffset = header.dataOffsetLongs();
        dataEndOffset = dataStartOffset + dataBlockEnd;
    }


    public BTreeHeader getHeader() {
        return header;
    }

    public int numEntries() {
        return header.numEntries();
    }

    // for testing
    boolean containsEntry(long key) {
        BTreePointer pointer = new BTreePointer(header);
        pointer.walkToData(key);
        return pointer.findData(key) >= 0;
    }

    public long findEntry(long key) {
        BTreePointer pointer = new BTreePointer(header);
        pointer.walkToData(key);
        return pointer.findData(key);
    }

    public long[] queryData(long[] keys, int offset) {
        long[] ret = new long[keys.length];
        BTreePointer pointer = new BTreePointer(header);

        if (header.layers() == 0) {
            pointer.walkToData(keys[0]);
            pointer.getValues(keys, ret, 0, offset);
        }
        else {
            int i = 0;
            while (i < keys.length) {
                pointer.walkToData(keys[i]);
                i+=pointer.getValues(keys, ret, i, offset);
                pointer.resetToRoot();
            }
        }

        return ret;
    }

    /** Keeps all items in buffer that exist in the btree */
    public void retainEntries(LongQueryBuffer buffer) {
        if (!buffer.hasMore())
            return;

        BTreePointer pointer = new BTreePointer(header);
        if (header.layers() == 0) {
            do {
                pointer.retainData(buffer);
            } while (buffer.hasMore());
        }
        else do {
            long val = buffer.currentValue();

            pointer.walkToData(val);
            pointer.retainData(buffer);

            pointer.resetToRoot();
        } while (buffer.hasMore());
    }

    /** Removes all items in buffer that exist in the btree */
    public void rejectEntries(LongQueryBuffer buffer) {
        if (!buffer.hasMore())
            return;


        BTreePointer pointer = new BTreePointer(header);
        if (header.layers() == 0) {
            do {
                pointer.rejectData(buffer);
            } while (buffer.hasMore());
        }
        else do {
            long val = buffer.currentValue();

            pointer.walkToData(val);
            pointer.rejectData(buffer);

            pointer.resetToRoot();
        } while (buffer.hasMore());
    }

    @CheckReturnValue
    public int readData(LongArray buf, int n, long pos) {
        long pageOffset = ((dataStartOffset&-ctx.pageSize()) + pos);

        try (var page = indexPool.get(8 * pageOffset, BufferEvictionPolicy.READ_ONCE, BufferReadaheadPolicy.AGGRESSIVE)) {
            int relOffset = (int) ((pos + dataStartOffset) % ctx.pageSize());
            n = (int) Math.min(n, Math.min(header.numEntries() * ctx.entrySize - pos, ctx.pageSize() - relOffset));
            page.get(relOffset, relOffset + n, buf, 0);
        }

        // FIXME: We may need to get the next page

        return n;
    }

    private class BTreePointer {
        private final long[] layerOffsets;

        private int layer;
        private long pointerOffset;
        private long maxValueInBlock;

        public String toString() {
            return getClass().getSimpleName() + "[" +
                "layer = " + layer + " ," +
                "offset = " + pointerOffset + "]";
        }

        public BTreePointer(BTreeHeader header) {
            layer = header.layers() - 1;
            pointerOffset = 0;
            layerOffsets = header.getRelativeLayerOffsets(ctx);
            maxValueInBlock = Long.MAX_VALUE;
        }

        public void resetToRoot() {
            this.layer = header.layers() - 1;
            this.pointerOffset = 0;
            this.maxValueInBlock = Long.MAX_VALUE;
        }

        /** Move the pointer to the next layer in the direction of the provided key */
        public void walkTowardChild(long key) {

            final long searchStart = indexStartOffset + layerOffsets[layer] + pointerOffset;

            try (var buffer = indexPool.get(8 * searchStart, BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.SMALL)) {
                final long nextLayerOffset = buffer.binarySearch(key, 0, ctx.pageSize());
                maxValueInBlock = buffer.get(nextLayerOffset);
                layer--;
                pointerOffset = ctx.pageSize() * (pointerOffset + nextLayerOffset);
            }
        }

        /** Move the pointer to the data layer associated with key */
        public void walkToData(long key) {
            if (layerOffsets.length > 0) {
                while (!isDataLayer()) {
                    walkTowardChild(key);
                }
            }
            else {
                final long blockStart = pointerOffset * ctx.entrySize;
                final long remainingTotal = dataBlockEnd - pointerOffset * ctx.entrySize;

                long blockEnd = min(ctx.pageSize(), remainingTotal) + dataStartOffset % ctx.pageSize();

                try (var page = dataPool.get(8 * ((dataStartOffset + blockStart) & -ctx.pageSize()), BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.NONE)) {
                    maxValueInBlock = page.get(blockEnd - ctx.entrySize);
                }
            }
        }

        public boolean isDataLayer() {
            return layer < 0;
        }

        public long findData(long key) {
            if (layer >= 0) {
                throw new IllegalStateException("Looking for data in an index layer");
            }

            final long searchStart = pointerOffset * ctx.entrySize;
            final long remainingTotal = dataBlockEnd - pointerOffset * ctx.entrySize;
            final long remainingBlock;

            if (layerOffsets.length == 0) {
                remainingBlock = remainingTotal;
            }
            else {
                remainingBlock = (long) ctx.pageSize() * ctx.entrySize;
            }

            try (var page = dataPool.get(8 * ((dataStartOffset + searchStart) & -ctx.pageSize()), BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.NONE)) {
                long blockOffset = (dataStartOffset + searchStart) % ctx.pageSize();
                long valueLocation = page.binarySearchN(ctx.entrySize, key, blockOffset, blockOffset + min(remainingTotal, remainingBlock));
                if (page.get(valueLocation) == key) {
                    return dataStartOffset + key;
                }
                else {
                    return -1 - valueLocation;
                }
            }
        }

        /** Retain any data entry matching the current key
         * in the buffer within the current data block.
         * <p></p>
         * This is much faster than looping with findData() and retain() for each key
         * since the index doesn't need to be re-traversed.
         * */
        public void retainData(LongQueryBuffer buffer) {

            final long searchStart = pointerOffset * ctx.entrySize + dataStartOffset % ctx.pageSize();
            final long remainingTotal = dataBlockEnd - pointerOffset * ctx.entrySize;
            final long remainingBlock;

            if (layerOffsets.length == 0) {
                remainingBlock = remainingTotal;
            }
            else {
                remainingBlock = (long) ctx.pageSize() * ctx.entrySize;
            }

            if (0 == remainingBlock) {
                while (buffer.hasMore()) {
                    buffer.rejectAndAdvance();
                }
            }

            long key = buffer.currentValue();

            try (var page = dataPool.get(8 * ((dataStartOffset + searchStart) & -ctx.pageSize()),
                    BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.AGGRESSIVE))
            {
                long blockOffset = searchStart % ctx.pageSize();
                long valueLocation = page.binarySearchN(ctx.entrySize, key, blockOffset, blockOffset + min(remainingTotal, remainingBlock));


                if (page.get(valueLocation) == key) {
                    buffer.retainAndAdvance();
                }
                else {
                    buffer.rejectAndAdvance();
                }

                if (buffer.hasMore() && buffer.currentValue() <= maxValueInBlock) {
                    page.retainN(buffer, ctx.entrySize, maxValueInBlock, valueLocation,  blockOffset + min(remainingTotal, remainingBlock));
                }
            }
        }

        /** Reject any data entry matching the current key in the buffer within the current data block
         * <p></p>
         * This is much faster than looping with findData() and retain() for each key
         * since the index doesn't need to be re-traversed.
         * */
        public void rejectData(LongQueryBuffer buffer) {

            final long searchStart = pointerOffset * ctx.entrySize + dataStartOffset % ctx.pageSize();
            final long remainingTotal = dataBlockEnd - pointerOffset * ctx.entrySize;
            final long remainingBlock;

            if (layerOffsets.length == 0) {
                remainingBlock = remainingTotal;
            }
            else {
                remainingBlock = (long) ctx.pageSize() * ctx.entrySize;
            }

            long key = buffer.currentValue();

            if (0 == remainingBlock) {
                while (buffer.hasMore()) {
                    buffer.retainAndAdvance();
                }
            }


            try (var page = dataPool.get(8 * ((dataStartOffset + searchStart) & -ctx.pageSize()),
                    BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.AGGRESSIVE))
            {
                long blockOffset = searchStart % ctx.pageSize();

                long valueLocation = page.binarySearchN(ctx.entrySize, key, blockOffset, blockOffset + min(remainingTotal, remainingBlock));

                if (page.get(valueLocation) == key) {
                    buffer.rejectAndAdvance();
                }
                else {
                    buffer.retainAndAdvance();
                }

                if (buffer.hasMore() && buffer.currentValue() <= maxValueInBlock) {
                    page.rejectN(buffer, ctx.entrySize, maxValueInBlock, valueLocation,  blockOffset + min(remainingTotal, remainingBlock));
                }
            }

        }

        public int getValues(long[] keys, long[] ret, int i, int offset) {
            final long searchStart = pointerOffset * ctx.entrySize + dataStartOffset % ctx.pageSize();
            final long remainingTotal = dataBlockEnd - pointerOffset * ctx.entrySize;
            final long remainingBlock;

            if (layerOffsets.length == 0) {
                remainingBlock = Math.min(remainingTotal, (long) ctx.pageSize() * ctx.entrySize);
            }
            else {
                remainingBlock = (long) ctx.pageSize() * ctx.entrySize;
            }

            try (var page = dataPool.get(8 * ((dataStartOffset + searchStart) & -ctx.pageSize()),
                    BufferEvictionPolicy.READ_ONCE, BufferReadaheadPolicy.AGGRESSIVE))
            {

                long blockOffset = searchStart % ctx.pageSize();

                int j;

                for (j = i; j < keys.length && keys[j] <= maxValueInBlock; j++) {
                    long valueLocation = page.binarySearchN(ctx.entrySize, keys[j], blockOffset, blockOffset + min(remainingTotal, remainingBlock));
                    if (page.get(valueLocation) == keys[j]) {
                        ret[j] = page.get(valueLocation + offset);
                    }
                }

                return j - i;

            }
        }
    }


}
