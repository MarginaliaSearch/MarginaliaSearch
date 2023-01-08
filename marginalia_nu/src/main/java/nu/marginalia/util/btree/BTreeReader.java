package nu.marginalia.util.btree;

import lombok.SneakyThrows;
import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.algo.LongArraySearch;
import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.array.delegate.ShiftedLongArray;
import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;

import static java.lang.Math.min;

public class BTreeReader implements BTreeReaderIf {

    private final LongArray index;
    private final ShiftedLongArray data;

    public final BTreeContext ctx;
    private final BTreeHeader header;

    private final long dataBlockEnd;

    public BTreeReader(LongArray file, BTreeContext ctx, long offset) {
        this.ctx = ctx;
        this.header = createHeader(file, offset);

        dataBlockEnd = (long) ctx.entrySize() * header.numEntries();
        index = file.range(header.indexOffsetLongs(), header.dataOffsetLongs());
        data = file.range(header.dataOffsetLongs(), header.dataOffsetLongs() + dataBlockEnd);

    }

    public static BTreeHeader createHeader(LongArray file, long fileOffset) {
        long[] parts = new long[3];
        file.get(fileOffset, fileOffset+3, parts);
        return new BTreeHeader(parts[0], parts[1], parts[2]);
    }

    public BTreeHeader getHeader() {
        return header;
    }

    public int numEntries() {
        return header.numEntries();
    }

    @SneakyThrows
    public void retainEntries(LongQueryBuffer buffer) {
        if (header.layers() == 0) {
            BTreePointer pointer = new BTreePointer(header);
            while (buffer.hasMore()) {
                pointer.retainData(buffer);
            }
        }
        retainSingle(buffer);
    }

    @SneakyThrows
    public void rejectEntries(LongQueryBuffer buffer) {
        if (header.layers() == 0) {
            BTreePointer pointer = new BTreePointer(header);
            while (buffer.hasMore()) {
                pointer.rejectData(buffer);
            }
        }
        rejectSingle(buffer);
    }

    private void retainSingle(LongQueryBuffer buffer) {

        BTreePointer pointer = new BTreePointer(header);

        for (; buffer.hasMore(); pointer.resetToRoot()) {

            long val = buffer.currentValue();

            if (!pointer.walkToData(val)) {
                buffer.rejectAndAdvance();
                continue;
            }

            pointer.retainData(buffer);
        }
    }

    private void rejectSingle(LongQueryBuffer buffer) {
        BTreePointer pointer = new BTreePointer(header);

        for (; buffer.hasMore(); pointer.resetToRoot()) {

            long val = buffer.currentValue();

            if (pointer.walkToData(val) && pointer.containsData(val)) {
                buffer.rejectAndAdvance();
            }
            else {
                buffer.retainAndAdvance();
            }
        }
    }


    /**
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

    public long[] queryData(long[] keys, int offset) {
        BTreePointer pointer = new BTreePointer(header);

        long[] ret = new long[keys.length];

        // this function could be re-written like retain() and would be
        // much faster

        if (header.layers() == 0) {
            long searchStart = 0;
            for (int i = 0; i < keys.length; i++) {
                long key = keys[i];
                searchStart = data.binarySearchN(ctx.entrySize(), key, searchStart, data.size);
                if (searchStart < 0) {
                    searchStart = LongArraySearch.decodeSearchMiss(searchStart);
                }
                else {
                    ret[i] = data.get(searchStart + offset);
                }
            }

        }
        else {
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

            final long nextLayerOffset = (int) index.binarySearchUpperBound(key, searchStart, searchStart + ctx.BLOCK_SIZE_WORDS()) - searchStart;

            layer --;
            boundary = index.get(searchStart + nextLayerOffset);
            offset = ctx.BLOCK_SIZE_WORDS() * (offset + nextLayerOffset);

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

            long searchStart = offset * ctx.entrySize();
            long remainingTotal = dataBlockEnd - offset * ctx.entrySize();
            long remainingBlock;

            remainingBlock = (layerOffsets.length == 0)
                    ? remainingTotal
                    : (long) ctx.BLOCK_SIZE_WORDS() * ctx.entrySize();

            long searchEnd = searchStart + (int) min(remainingTotal, remainingBlock);

            return data.binarySearchN(ctx.entrySize(), key, searchStart, searchEnd);
        }

        public void retainData(LongQueryBuffer buffer) {

            long dataOffset = findData(buffer.currentValue());
            if (dataOffset >= 0) {
                buffer.retainAndAdvance();

                if (buffer.hasMore() && buffer.currentValue() <= boundary) {
                    long blockBase = offset * ctx.entrySize();
                    long relOffset = dataOffset - blockBase;

                    long remainingTotal = dataBlockEnd - dataOffset;
                    long remainingBlock = ctx.BLOCK_SIZE_WORDS() - relOffset;

                    long searchEnd = dataOffset + (int) min(remainingTotal, remainingBlock);

                    data.range(dataOffset, searchEnd).retainN(buffer, ctx.entrySize(), boundary);
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
                    long blockBase = offset * ctx.entrySize();
                    long relOffset = dataOffset - blockBase;

                    long remainingTotal = dataBlockEnd - dataOffset;
                    long remainingBlock = ctx.BLOCK_SIZE_WORDS() - relOffset;

                    long searchEnd = dataOffset + (int) min(remainingTotal, remainingBlock);

                    data.range(dataOffset, searchEnd).rejectN(buffer, ctx.entrySize(), boundary);
                }
            }
            else {
                buffer.retainAndAdvance();
            }
        }
    }


}
