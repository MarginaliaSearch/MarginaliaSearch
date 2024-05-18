package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.btree.model.BTreeHeader;

import java.util.Arrays;

import static java.lang.Math.min;

public class BTreeReader {

    private final LongArray index;
    private final LongArray data;

    public final BTreeContext ctx;
    private final BTreeHeader header;

    private final long dataBlockEnd;

    public BTreeReader(LongArray file, BTreeContext ctx, long offset) {
        this.ctx = ctx;
        this.header = readHeader(file, offset);

        dataBlockEnd = (long) ctx.entrySize * header.numEntries();
        index = file.range(header.indexOffsetLongs(), header.dataOffsetLongs());
        data = file.range(header.dataOffsetLongs(), header.dataOffsetLongs() + dataBlockEnd);

        assert file.size() >= header.dataOffsetLongs() + dataBlockEnd;
    }

    public LongArray data() {
        return data;
    }
    public LongArray index() {
        return index;
    }

    public static BTreeHeader readHeader(LongArray file, long fileOffset) {
        return new BTreeHeader(file, fileOffset);
    }

    public BTreeHeader getHeader() {
        return header;
    }

    public int numEntries() {
        return header.numEntries();
    }

    /** Keeps all items in buffer that exist in the btree */
    public void retainEntries(LongQueryBuffer buffer) {
        BTreePointer pointer = new BTreePointer(header);
        if (header.layers() == 0) {
            while (buffer.hasMore()) {
                pointer.retainData(buffer);
            }
        }
        else while (buffer.hasMore()) {
            long val = buffer.currentValue();

            pointer.walkToData(val);
            pointer.retainData(buffer);

            pointer.resetToRoot();
        }
    }

    /** Removes all items in buffer that exist in the btree */
    public void rejectEntries(LongQueryBuffer buffer) {
        BTreePointer pointer = new BTreePointer(header);
        if (header.layers() == 0) {
            while (buffer.hasMore()) {
                pointer.rejectData(buffer);
            }
        }
        else while (buffer.hasMore()) {
            long val = buffer.currentValue();

            pointer.walkToData(val);
            pointer.rejectData(buffer);

            pointer.resetToRoot();
        }
    }


    /** Locate entry in btree
     *
     * @return file offset of entry matching keyRaw, negative if absent
     */
    public long findEntry(final long key) {
        BTreePointer ip = new BTreePointer(header);

        ip.walkToData(key);

        return ip.findData(key);
    }

    public void readData(LongArray buf, int n, long pos) {
        data.get(pos, pos + n, buf, 0);
    }

    /** Used for querying interlaced data in the btree.
     * <p>
     * If entry size is e.g. 2, the data is positioned like [key1, data1, key2, data2, key3, data3]
     * then given keys=[key1, key3], and offset=1 (i.e. look 1 step to the right), the return value will be
     * [data1, data3].
     * <p>
     * For each item in the keys array where the key is not found in the btree, the value will be zero.
     * <p>
     * Caveat: The keys are assumed to be sorted.
     */
    public long[] queryData(long[] keys, int offset) {

        assert(isSorted(keys)) : "The input array docIds is assumed to be sorted";

        if (header.layers() == 0) {
            return queryDataNoIndex(keys, offset);
        }
        else {
            return queryDataWithIndex(keys, offset);
        }
    }

    private boolean isSorted(long[] keys) {
        for (int i = 1; i < keys.length; i++) {
            if (keys[i] < keys[i-1])
                return false;
        }
        return true;
    }

    // This b-tree doesn't have any index and is actually just a sorted list of items
    private long[] queryDataNoIndex(long[] keys, int offset) {
        long[] ret = new long[keys.length];

        long searchStart = 0;
        for (int i = 0; i < keys.length; i++) {
            long key = keys[i];
            searchStart = data.binarySearchN(ctx.entrySize, key, searchStart, data.size());
            if (data.get(searchStart) == key) {
                ret[i] = data.get(searchStart + offset);
            }
        }
        return ret;
    }

    private long[] queryDataWithIndex(long[] keys, int offset) {
        BTreePointer pointer = new BTreePointer(header);
        long[] ret = Arrays.copyOf(keys, keys.length);

        for (int i = 0; i < keys.length;) {
            pointer.walkToData(keys[i]);
            i = pointer.extractData(ret, i, offset);
            pointer.resetToRoot();
        }
        return ret;
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

            final long searchStart = layerOffsets[layer] + pointerOffset;

            final long nextLayerOffset = index.binarySearch(key, searchStart, searchStart + ctx.pageSize()) - searchStart;

            layer --;
            maxValueInBlock = index.get(searchStart + nextLayerOffset);
            pointerOffset = ctx.pageSize() * (pointerOffset + nextLayerOffset);
        }

        /** Move the pointer to the data layer associated with key */
        public void walkToData(long key) {
            while (!isDataLayer()) {
                walkTowardChild(key);
            }
        }

        public boolean isDataLayer() {
            return layer < 0;
        }

        /** Find the data entry matching key
         *
         * @return file offset of entry matching keyRaw, negative if absent
         */
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

            long searchEnd = searchStart + min(remainingTotal, remainingBlock);

            long ret = data.binarySearchN(ctx.entrySize, key, searchStart, searchEnd);
            if (data.get(ret) == key) {
                return ret;
            }
            else {
                return -1 - ret;
            }
        }

        public int extractData(long[] input, int idx, int offset) {

            long dataOffset = findData(input[idx]);

            if (dataOffset >= 0) {
                input[idx++] = data.get(dataOffset + offset);

                if (idx < input.length && input[idx] < maxValueInBlock) {
                    long relOffsetInBlock = dataOffset - pointerOffset * ctx.entrySize;

                    long remainingTotal = dataBlockEnd - dataOffset;
                    long remainingBlock = ctx.pageSize() - relOffsetInBlock; // >= 0

                    long searchEnd = dataOffset + min(remainingTotal, remainingBlock);

                    while (dataOffset < searchEnd
                        && idx < input.length
                        && input[idx] <= maxValueInBlock)
                    {
                        long value = data.get(dataOffset);

                        if (value == input[idx]) {
                            input[idx++] = data.get(dataOffset + offset);
                            dataOffset += ctx.entrySize;
                        }
                        else if (value > input[idx]) {
                            input[idx++] = 0;
                        }
                        else if (value < input[idx]) {
                            dataOffset += ctx.entrySize;
                        }
                    }
                }
            }
            else {
                input[idx++] = 0;
            }

            return idx;
        }
        /** Retain any data entry matching the current key
         * in the buffer within the current data block.
         * <p></p>
         * This is much faster than looping with findData() and retain() for each key
         * since the index doesn't need to be re-traversed.
         * */
        public void retainData(LongQueryBuffer buffer) {

            long dataOffset = findData(buffer.currentValue());
            if (dataOffset >= 0) {
                buffer.retainAndAdvance();

                if (buffer.hasMore() && buffer.currentValue() <= maxValueInBlock) {
                    long relOffsetInBlock = dataOffset - pointerOffset * ctx.entrySize;

                    long remainingTotal = dataBlockEnd - dataOffset;
                    long remainingBlock = ctx.pageSize() - relOffsetInBlock; // >= 0

                    long searchEnd = dataOffset + min(remainingTotal, remainingBlock);

                    data.retainN(buffer, ctx.entrySize, maxValueInBlock, dataOffset, searchEnd);
                }
            }
            else {
                buffer.rejectAndAdvance();
            }

        }

        /** Reject any data entry matching the current key in the buffer within the current data block
         * <p></p>
         * This is much faster than looping with findData() and retain() for each key
         * since the index doesn't need to be re-traversed.
         * */
        public void rejectData(LongQueryBuffer buffer) {

            long dataOffset = findData(buffer.currentValue());
            if (dataOffset >= 0) {
                buffer.rejectAndAdvance();

                if (buffer.hasMore() && buffer.currentValue() <= maxValueInBlock) {
                    long relOffsetInBlock = dataOffset - pointerOffset * ctx.entrySize;

                    long remainingTotal = dataBlockEnd - dataOffset;
                    long remainingBlock = ctx.pageSize() - relOffsetInBlock; // >= 0

                    long searchEnd = dataOffset + min(remainingTotal, remainingBlock);

                    data.rejectN(buffer, ctx.entrySize, maxValueInBlock, dataOffset, searchEnd);
                }
            }
            else {
                buffer.retainAndAdvance();
            }
        }
    }


}
