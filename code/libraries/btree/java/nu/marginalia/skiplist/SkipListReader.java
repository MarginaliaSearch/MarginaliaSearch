package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferPool;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

public class SkipListReader {

    private final BufferPool pool;
    private final long blockStart;

    private long currentBlock;
    private int currentBlockOffset;
    private int currentIdx;

    private boolean atEnd;

    public SkipListReader(BufferPool pool, long blockStart) {
        this.pool = pool;
        this.blockStart = blockStart;

        currentBlock = blockStart & -SkipListConstants.BLOCK_SIZE;
        currentBlockOffset = (int) (blockStart & (SkipListConstants.BLOCK_SIZE - 1));
        atEnd = false;

        currentIdx = 0;
    }

    public void reset() {
        currentBlock = blockStart & -SkipListConstants.BLOCK_SIZE;
        currentBlockOffset = (int) (blockStart & (SkipListConstants.BLOCK_SIZE - 1));
        currentIdx = 0;

        atEnd = false;
    }

    public boolean atEnd() {
        return atEnd;
    }

    public int getRemainingSize() {
        try (var page = pool.get(currentBlock)) {
            var ms = page.getMemorySegment();
            return ms.get(ValueLayout.JAVA_INT, 4 + currentBlockOffset) - currentIdx;
        }
    }

    public void retainData(@NotNull LongQueryBuffer data) {
        while (data.hasMore()) {
            try (var page = pool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();

                int n = headerNumRecords(ms, currentBlockOffset);
                int fc = headerForwardCount(ms, currentBlockOffset);
                byte flags = (byte) headerFlags(ms, currentBlockOffset);

                int dataOffset = currentBlockOffset + 8 * (1 + fc);

                if (currentIdx < n) {
                    long pv = ms.get(ValueLayout.JAVA_LONG, dataOffset + currentIdx * 8L);
                    long bv = data.currentValue();

                    while (data.hasMore()) {

                        if (bv < pv) {
                            if (!data.rejectAndAdvance()) break;
                            bv = data.currentValue();
                            continue;
                        }
                        else if (bv == pv) {
                            if (!data.retainAndAdvance()) break;
                            bv = data.currentValue();
                            continue;
                        }

                        if (++currentIdx < n) {
                            pv = ms.get(ValueLayout.JAVA_LONG, dataOffset + currentIdx * 8L);
                        }
                        else {
                            break;
                        }
                    }
                }

                if (currentIdx == n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.rejectAndAdvance();
                        return;
                    }
                    currentBlockOffset = 0;
                    currentIdx = 0;
                    if (!data.hasMore()) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                    }
                    else {
                        long nextBlock = currentBlock + SkipListConstants.BLOCK_SIZE;
                        for (int i = 0; i < fc; i++) {
                            long max = ms.get(ValueLayout.JAVA_LONG, currentBlockOffset + SkipListConstants.HEADER_SIZE + 8L * i);
                            if (max < data.currentValue()) {
                                nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * (SkipListWriter.skipOffsetForPointer(i) + 1);
                            } else {
                                break;
                            }
                        }
                        currentBlock = nextBlock;
                    }
                }
            }
        }
    }

    public long[] getValues(long[] keys) {
        int pos = 0;
        long[] vals = new long[keys.length];

        while (pos < keys.length) {
            try (var page = pool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();
                assert ms.get(ValueLayout.JAVA_INT, currentBlockOffset) != 0 : "Likely reading zero space @ " + currentBlockOffset + " starting at " + blockStart + " -- " + parseBlock(ms, currentBlockOffset);

                int n = headerNumRecords(ms, currentBlockOffset);
                int fc = headerForwardCount(ms, currentBlockOffset);
                byte flags = (byte) headerFlags(ms, currentBlockOffset);

                int dataOffset = currentBlockOffset + 8 * (1 + fc);
                int valuesOffset = currentBlockOffset + 8 * (1 + fc + n);

                if (currentIdx < n) {
                    long pv = ms.get(ValueLayout.JAVA_LONG, dataOffset + currentIdx * 8L);
                    long bv = keys[pos];

                    for (;;) {

                        if (bv < pv) {
                            if (++pos >= keys.length) break;
                            bv = keys[pos];
                            continue;
                        }
                        else if (bv == pv) {
                            vals[pos] = ms.get(ValueLayout.JAVA_LONG, valuesOffset + 8L*currentIdx);
                            if (++pos >= keys.length) break;
                            bv = keys[pos];
                            continue;
                        }

                        if (++currentIdx < n) {
                            pv = ms.get(ValueLayout.JAVA_LONG, dataOffset + currentIdx * 8L);
                        }
                        else {
                            break;
                        }
                    }
                }

                if (currentIdx == n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {

                        break;
                    }
                    currentBlockOffset = 0;
                    currentIdx = 0;
                    if (pos >= keys.length) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                    }
                    else {
                        long nextBlock = currentBlock + SkipListConstants.BLOCK_SIZE;
                        for (int i = 0; i < fc; i++) {
                            long max = ms.get(ValueLayout.JAVA_LONG, currentBlockOffset + SkipListConstants.HEADER_SIZE + 8L * i);
                            if (max < keys[pos]) {
                                nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * (SkipListWriter.skipOffsetForPointer(i) + 1);
                            } else {
                                break;
                            }
                        }
                        currentBlock = nextBlock;
                    }
                }
            }
        }

        return vals;
    }

    public void rejectData(@NotNull LongQueryBuffer data) {
        while (data.hasMore()) {
            try (var page = pool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();

                int n = headerNumRecords(ms, currentBlockOffset);
                int fc = headerForwardCount(ms, currentBlockOffset);
                byte flags = (byte) headerFlags(ms, currentBlockOffset);

                int dataOffset = currentBlockOffset + 8 * (1 + fc);

                if (currentIdx < n) {
                    long pv = ms.get(ValueLayout.JAVA_LONG, dataOffset + currentIdx * 8L);
                    long bv = data.currentValue();

                    while (data.hasMore()) {

                        if (bv < pv) {
                            if (!data.retainAndAdvance()) break;
                            bv = data.currentValue();
                            continue;
                        }
                        else if (bv == pv) {
                            if (!data.rejectAndAdvance()) break;
                            bv = data.currentValue();
                            continue;
                        }

                        if (++currentIdx < n) {
                            pv = ms.get(ValueLayout.JAVA_LONG, dataOffset + currentIdx * 8L);
                        }
                        else {
                            break;
                        }
                    }
                }

                if (currentIdx == n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.retainAndAdvance();
                        return;
                    }
                    currentBlockOffset = 0;
                    currentIdx = 0;
                    if (!data.hasMore()) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                    }
                    else {
                        long nextBlock = currentBlock + SkipListConstants.BLOCK_SIZE;
                        for (int i = 0; i < fc; i++) {
                            long max = ms.get(ValueLayout.JAVA_LONG, currentBlockOffset + SkipListConstants.HEADER_SIZE + 8L * i);
                            if (max < data.currentValue()) {
                                nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * (SkipListWriter.skipOffsetForPointer(i) + 1);
                            } else {
                                break;
                            }
                        }
                        currentBlock = nextBlock;
                    }
                }
            }


        }
    }

    public int getData(@NotNull LongQueryBuffer dest)
    {
        if (atEnd) return 0;

        int totalCopied = 0;
        while (dest.fitsMore() && !atEnd) {
            try (var page = pool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();
                assert ms.get(ValueLayout.JAVA_INT, currentBlockOffset) != 0 : "Likely reading zero space";
                int n = headerNumRecords(ms, currentBlockOffset);
                int fc = headerForwardCount(ms, currentBlockOffset);
                byte flags = (byte) headerFlags(ms, currentBlockOffset);

                int dataOffset = currentBlockOffset + 8 * (1 + fc);
                int nCopied = dest.addData(ms, dataOffset, n - currentIdx);
                currentIdx += nCopied;

                if (currentIdx >= n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (!atEnd) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                        currentBlockOffset = 0;
                        currentIdx = 0;
                    }
                }

                totalCopied += nCopied;
            }
        }

        return totalCopied;
    }


    public record RecordView(int n,
                             int fc,
                             int size,
                             int flags,
                             LongList fowardPointers,
                             LongList docIds,
                             LongList values)
    {
        public long highestDocId() {
            return docIds.getLast();
        }
    }

    public static RecordView parseBlock(MemorySegment seg, int offset) {
        int n = headerNumRecords(seg, offset);
        int fc = headerForwardCount(seg, offset);
        int flags = headerFlags(seg, offset);
        int size = headerSize(seg, offset);

        assert n < SkipListConstants.MAX_RECORDS_PER_BLOCK : "Invalid header, n = " + n;

        offset += SkipListConstants.HEADER_SIZE;

        LongList forwardPointers = new LongArrayList();
        for (int i = 0; i < fc; i++) {
            forwardPointers.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L*i));
        }
        offset += 8*fc;

        LongList docIds = new LongArrayList();
        for (int i = 0; i < n; i++) {
            docIds.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L*i));
        }
        offset += 8*n;
        LongList values = new LongArrayList();
        for (int i = 0; i < n; i++) {
            values.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L*i));
        }

        return new RecordView(n, fc, size, flags, forwardPointers, docIds, values);
    }

    public static List<RecordView> parseBlocks(MemorySegment seg, int offset) {
        List<RecordView> ret = new ArrayList<>();
        RecordView block;
        do {
            block = parseBlock(seg, offset);
            ret.add(block);
            offset = (offset + 512) & -512;
        } while (0 == (block.flags & SkipListConstants.FLAG_END_BLOCK));

        return ret;
    }

    public static int headerNumRecords(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_BYTE, offset);
    }

    public static int headerForwardCount(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_BYTE, offset + 1);
    }

    public static int headerFlags(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_BYTE, offset + 2);
    }

    public static int headerSize(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_INT, offset + 4);
    }

    public static int docIdsOffset(MemorySegment block, int offset) {
        return offset + SkipListConstants.HEADER_SIZE + 8 * headerForwardCount(block, offset);
    }

    public static int valuesOffset(MemorySegment block, int offset) {
        return offset + SkipListConstants.HEADER_SIZE + 8 * (headerForwardCount(block, offset) + headerNumRecords(block, offset));
    }

}
