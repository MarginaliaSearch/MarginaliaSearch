package nu.marginalia.util.btree;

import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import lombok.SneakyThrows;
import nu.marginalia.util.btree.model.BTreeContext;
import nu.marginalia.util.btree.model.BTreeHeader;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.util.multimap.MultimapSearcher;

import static java.lang.Math.min;

public class BTreeReader {

    private final MultimapFileLong file;
    public final BTreeContext ctx;

    private final MultimapSearcher indexSearcher;
    private final MultimapSearcher dataSearcher;
    private final BTreeHeader header;

    public BTreeReader(MultimapFileLong file, BTreeContext ctx, BTreeHeader header) {
        this.file = file;
        this.indexSearcher = MultimapSearcher.forContext(file, ~0, 1);
        this.dataSearcher = MultimapSearcher.forContext(file, ctx.equalityMask(), ctx.entrySize());

        this.ctx = ctx;
        this.header = header;
    }

    public BTreeReader(MultimapFileLong file, BTreeContext ctx, long offset) {
        this.file = file;
        this.indexSearcher = MultimapSearcher.forContext(file, ~0, 1);
        this.dataSearcher = MultimapSearcher.forContext(file, ctx.equalityMask(), ctx.entrySize());

        this.ctx = ctx;
        this.header = createHeader(file, offset);
    }

    public static BTreeHeader createHeader(MultimapFileLong file, long fileOffset) {
        return new BTreeHeader(file.get(fileOffset), file.get(fileOffset+1), file.get(fileOffset+2));
    }

    public BTreeHeader getHeader() {
        return header;
    }

    public int numEntries() {
        return header.numEntries();
    }

    @SneakyThrows
    public void retainEntries(BTreeQueryBuffer buffer) {
        if (header.layers() == 0) {
            BTreePointer pointer = new BTreePointer(header);
            pointer.retainData(buffer);
        }
        retainSingle(buffer);
    }

    @SneakyThrows
    public void rejectEntries(BTreeQueryBuffer buffer) {
        if (header.layers() == 0) {
            BTreePointer pointer = new BTreePointer(header);
            pointer.rejectData(buffer);
        }
        rejectSingle(buffer);
    }

    private void retainSingle(BTreeQueryBuffer buffer) {

        BTreePointer pointer = new BTreePointer(header);

        for (; buffer.hasMore(); pointer.resetToRoot()) {

            long val = buffer.currentValue() & ctx.equalityMask();

            if (!pointer.walkToData(val)) {
                buffer.rejectAndAdvance();
                continue;
            }

            pointer.retainData(buffer);
        }
    }

    private void rejectSingle(BTreeQueryBuffer buffer) {
        BTreePointer pointer = new BTreePointer(header);

        for (; buffer.hasMore(); pointer.resetToRoot()) {

            long val = buffer.currentValue() & ctx.equalityMask();

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
    public long findEntry(final long keyRaw) {
        final long key = keyRaw & ctx.equalityMask();

        BTreePointer ip = new BTreePointer(header);

        while (!ip.isDataLayer())
            ip.walkToChild(key);

        return ip.findData(key);
    }

    public void readData(long[] data, int n, long pos) {
        file.read(data, n, header.dataOffsetLongs() + pos);
    }

    public long[] queryData(long[] urls, int offset) {
        BTreePointer pointer = new BTreePointer(header);

        long[] ret = new long[urls.length];

        for (int i = 0; i < urls.length; i++, pointer.resetToRoot()) {
            if (pointer.walkToData(urls[i])) {
                long dataAddress = pointer.findData(urls[i]);
                if (dataAddress >= 0) {
                    ret[i] = file.get(dataAddress + offset);
                }
            }
        }

        return ret;
    }

    /** Find the range of values so that prefixStart <= n < prefixNext */
    public LongLongImmutablePair getRangeForPrefix(long prefixStart, long prefixNext) {
        long lowerBoundStart = lowerBound(prefixStart);
        long lowerBoundEnd = lowerBound(prefixNext);

        return new LongLongImmutablePair(lowerBoundStart, lowerBoundEnd);
    }

    private long lowerBound(long key) {
        key &= ctx.equalityMask();

        BTreePointer ip = new BTreePointer(header);

        while (!ip.isDataLayer())
            ip.walkToChild(key);

        return ip.findDataLower(key);
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
            final long indexAddress = header.indexOffsetLongs();

            final long indexLayerBlockOffset = layerOffsets[layer] + offset;

            final long searchStart = indexAddress + indexLayerBlockOffset;
            final long nextLayerOffset = (int)(indexSearcher.binarySearchLower(key, searchStart, ctx.BLOCK_SIZE_WORDS()) - searchStart);

            if (nextLayerOffset < 0)
                return false;

            layer --;
            boundary = file.get(searchStart + offset);
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
            if (layer > 0) {
                throw new IllegalStateException("Looking for data in an index layer");
            }

            long searchStart = header.dataOffsetLongs() + offset * ctx.entrySize();
            int numEntries = min((int)(header.numEntries() - offset), ctx.BLOCK_SIZE_WORDS());

            return dataSearcher.binarySearch(key, searchStart, numEntries);
        }

        public long findDataLower(long key) {
            if (layer > 0) {
                throw new IllegalStateException("Looking for data in an index layer");
            }

            long searchStart = header.dataOffsetLongs() + offset * ctx.entrySize();
            int numEntries = min((int)(header.numEntries() - offset), ctx.BLOCK_SIZE_WORDS());

            return dataSearcher.binarySearchLower(key, searchStart, numEntries);
        }

        public void retainData(BTreeQueryBuffer buffer) {

            long dataOffset = findData(buffer.currentValue());
            if (dataOffset >= 0) {
                buffer.retainAndAdvance();

                long blockBase = header.dataOffsetLongs() + offset * ctx.entrySize();
                long relOffset = dataOffset - blockBase;

                int numEntries =
                        min((int) (header.numEntries() - relOffset), ctx.BLOCK_SIZE_WORDS()) / ctx.entrySize();

                if (buffer.currentValue() <= boundary) {
                    file.retain(buffer, boundary, dataOffset, numEntries, ctx.equalityMask(), ctx.entrySize());
                }
            }
            else {
                buffer.rejectAndAdvance();
            }

        }

        public void rejectData(BTreeQueryBuffer buffer) {

            long dataOffset = findData(buffer.currentValue());
            if (dataOffset >= 0) {
                buffer.rejectAndAdvance();

                long blockBase = header.dataOffsetLongs() + offset * ctx.entrySize();
                long relOffset = dataOffset - blockBase;

                int numEntries =
                        min((int) (header.numEntries() - relOffset), ctx.BLOCK_SIZE_WORDS()) / ctx.entrySize();

                if (buffer.currentValue() <= boundary) {
                    file.reject(buffer, boundary, dataOffset, numEntries, ctx.equalityMask(), ctx.entrySize());
                }
            }
            else {
                buffer.retainAndAdvance();
            }
        }
    }


}
