package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.LongArraySearch;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.array.delegate.ShiftedLongArray;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.btree.model.BTreeHeader;

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

    LongArray data() {
        return data;
    }
    LongArray index() {
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

            if (!pointer.walkToData(val)) {
                buffer.rejectAndAdvance();
            }
            else {
                pointer.retainData(buffer);
            }

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

            if (pointer.walkToData(val) && pointer.containsData(val)) {
                buffer.rejectAndAdvance();
            }
            else {
                buffer.retainAndAdvance();
            }

            pointer.resetToRoot();
        }
    }


    /** Locate entry in btree
     *
     * @return file offset of entry matching keyRaw, negative if absent
     */
    public long findEntry(final long key) {
        BTreePointer ip = new BTreePointer(header);

        while (!ip.isDataLayer())
            if (!ip.walkToChild(key))
                return -1;

        return ip.findData(key);
    }

    public void readData(long[] buf, int n, long pos) {
        data.get(pos, pos + n, buf);
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
            if (searchStart < 0) {
                searchStart = LongArraySearch.decodeSearchMiss(searchStart);
            }
            else {
                ret[i] = data.get(searchStart + offset);
            }
        }
        return ret;
    }

    private long[] queryDataWithIndex(long[] keys, int offset) {
        BTreePointer pointer = new BTreePointer(header);
        long[] ret = new long[keys.length];

        // FIXME: this function could be re-written like retain() and would be much faster
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                pointer.resetToRoot();
            }

            if (pointer.walkToData(keys[i])) {
                long dataAddress = pointer.findData(keys[i]);
                if (dataAddress >= 0) {
                    ret[i] = data.get(dataAddress + offset);
                }
            }
        }
        return ret;
    }

    private class BTreePointer {
        private final long[] layerOffsets;

        private int layer;
        private long offset;
        private long boundary;

        public String toString() {
            return getClass().getSimpleName() + "[" +
                "layer = " + layer + " ," +
                "offset = " + offset + "]";
        }

        public BTreePointer(BTreeHeader header) {
            layer = header.layers() - 1;
            offset = 0;
            layerOffsets = header.getRelativeLayerOffsets(ctx);
            boundary = Long.MAX_VALUE;
        }

        public void resetToRoot() {
            this.layer = header.layers() - 1;
            this.offset = 0;
            this.boundary = Long.MAX_VALUE;
        }

        public int layer() {
            return layer;
        }

        public boolean walkToChild(long key) {

            final long searchStart = layerOffsets[layer] + offset;

            final long nextLayerOffset = index.binarySearchUpperBound(key, searchStart, searchStart + ctx.pageSize()) - searchStart;

            layer --;
            boundary = index.get(searchStart + nextLayerOffset);
            offset = ctx.pageSize() * (offset + nextLayerOffset);


            return true;
        }

        public boolean walkToData(long key) {
            while (!isDataLayer()) {
                if (!walkToChild(key)) {
                    return false;
                }
            }
            return true;
        }

        public boolean isDataLayer() {
            return layer < 0;
        }

        public boolean containsData(long key) {
            return findData(key) >= 0;
        }

        public long findData(long key) {
            if (layer >= 0) {
                throw new IllegalStateException("Looking for data in an index layer");
            }

            long searchStart = offset * ctx.entrySize;
            long remainingTotal = dataBlockEnd - offset * ctx.entrySize;
            long remainingBlock;

            remainingBlock = (layerOffsets.length == 0)
                    ? remainingTotal
                    : (long) ctx.pageSize() * ctx.entrySize;

            long searchEnd = searchStart + min(remainingTotal, remainingBlock);

            return data.binarySearchN(ctx.entrySize, key, searchStart, searchEnd);
        }

        public void retainData(LongQueryBuffer buffer) {

            long dataOffset = findData(buffer.currentValue());
            if (dataOffset >= 0) {
                buffer.retainAndAdvance();

                if (buffer.hasMore() && buffer.currentValue() <= boundary) {
                    long blockBase = offset * ctx.entrySize;
                    long relOffset = dataOffset - blockBase;

                    long remainingTotal = dataBlockEnd - dataOffset;
                    long remainingBlock = ctx.pageSize() - relOffset;

                    long searchEnd = dataOffset + min(remainingTotal, remainingBlock);

                    data.retainN(buffer, ctx.entrySize, boundary, dataOffset, searchEnd);
                }
            }
            else {
                buffer.rejectAndAdvance();
            }

        }

        public void rejectData(LongQueryBuffer buffer) {

            long dataOffset = findData(buffer.currentValue());
            if (dataOffset >= 0) {
                buffer.rejectAndAdvance();

                if (buffer.hasMore() && buffer.currentValue() <= boundary) {
                    long blockBase = offset * ctx.entrySize;
                    long relOffset = dataOffset - blockBase;

                    long remainingTotal = dataBlockEnd - dataOffset;
                    long remainingBlock = ctx.pageSize() - relOffset;

                    long searchEnd = dataOffset + min(remainingTotal, remainingBlock);

                    data.rejectN(buffer, ctx.entrySize, boundary, dataOffset, searchEnd);
                }
            }
            else {
                buffer.retainAndAdvance();
            }
        }
    }


}
