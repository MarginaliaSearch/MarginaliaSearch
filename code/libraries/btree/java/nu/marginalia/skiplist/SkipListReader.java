package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.array.pool.MemoryPage;
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
    private int currentBlockIdx;

    private boolean atEnd;

    public SkipListReader(BufferPool pool, long blockStart) {
        this.pool = pool;
        this.blockStart = blockStart;

        currentBlock = blockStart & -SkipListConstants.BLOCK_SIZE;
        currentBlockOffset = (int) (blockStart & (SkipListConstants.BLOCK_SIZE - 1));
        atEnd = false;

        currentBlockIdx = 0;
    }

    public void reset() {
        currentBlock = blockStart & -SkipListConstants.BLOCK_SIZE;
        currentBlockOffset = (int) (blockStart & (SkipListConstants.BLOCK_SIZE - 1));
        currentBlockIdx = 0;

        atEnd = false;
    }

    public boolean atEnd() {
        return atEnd;
    }

    public int estimateSize() {
        try (var page = pool.get(currentBlock)) {
            int fc = headerForwardCount(page, currentBlockOffset);
            if (fc > 0) {
                return SkipListConstants.MAX_RECORDS_PER_BLOCK * SkipListConstants.skipOffsetForPointer(fc);
            }
            else {
                return headerNumRecords(page, currentBlockOffset);
            }
        }
    }

    boolean retainInPage(MemoryPage page, int dataOffset, int n, LongQueryBuffer data) {

        int matches = 0;
        while (currentBlockIdx < n && data.hasMore()) {
            currentBlockIdx = page.binarySearchLong(data.currentValue(), dataOffset, currentBlockIdx, n);
            if (currentBlockIdx >= n) break;

            long pv = page.getLong( dataOffset + currentBlockIdx * 8);
            long bv = data.currentValue();

            if (pv == bv) {
                data.retainAndAdvance();
                currentBlockIdx++;
                if (++matches > 5)
                    break;
            }
            else if (pv > bv) {
                data.rejectAndAdvance();
            }
            else {
                currentBlockIdx ++;
            }
        }

        if (currentBlockIdx < n && data.hasMore()) {
            long pv = page.getLong( dataOffset + currentBlockIdx * 8);
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

                if (++currentBlockIdx < n) {
                    pv = page.getLong( dataOffset + currentBlockIdx * 8);
                }
                else {
                    return true;
                }
            }
        }

        return currentBlockIdx >= n;
    }

    public void retainData(@NotNull LongQueryBuffer data) {
        while (data.hasMore()) {
            try (var page = pool.get(currentBlock)) {

                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);
                int flags = headerFlags(page, currentBlockOffset);

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);

                if (retainInPage(page, dataOffset, n, data)) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.rejectAndAdvance();
                        return;
                    }
                    currentBlockOffset = 0;
                    currentBlockIdx = 0;
                    if (!data.hasMore()) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                    }
                    else {
                        long nextBlock = currentBlock + SkipListConstants.BLOCK_SIZE;
                        for (int i = 0; i < fc; i++) {
                            long max = page.getLong(currentBlockOffset + SkipListConstants.HEADER_SIZE + 8 * i);
                            if (max < data.currentValue()) {
                                nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * (SkipListConstants.skipOffsetForPointer(i) + 1);
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

    public long[] getValueOffsets(long[] keys) {
        int pos = 0;
        long[] vals = new long[keys.length];

        int pageNo = 0;
        while (pos < keys.length) {
            try (var page = pool.get(currentBlock)) {

                MemorySegment ms = page.getMemorySegment();
                assert ms.get(ValueLayout.JAVA_INT, currentBlockOffset) != 0 : "Likely reading zero space @ " + currentBlockOffset + " starting at " + blockStart + " -- " + parseBlock(ms, currentBlockOffset);
                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                if (n == 0) {
                    throw new IllegalStateException("Reading null memory!");
                }

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);
                long valuesOffset = headerValuesBaseOffset(page, currentBlockOffset);
                if ((valuesOffset & 7) != 0) {
                    System.err.println(pageNo + " Values offset invalid at " + currentBlock + ":" + currentBlockOffset);
                    throw new IllegalStateException(parseBlock(ms, currentBlockOffset).toString());
                }

                while (currentBlockIdx < n && pos < keys.length) {
                    currentBlockIdx = page.binarySearchLong(keys[pos], dataOffset, currentBlockIdx, n);
                    if (currentBlockIdx >= n) break;

                    long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                    long bv = keys[pos];

                    if (pv == bv) {
                        vals[pos++] = 1L + valuesOffset + currentBlockIdx * 8L;
                        currentBlockIdx++;
                        break;
                    }
                    else if (pv > bv) {
                        pos++;
                    }
                    else {
                        currentBlockIdx ++;
                    }

                }

                if (currentBlockIdx < n && pos < keys.length) {
                    long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                    long bv = keys[pos];

                    for (;;) {

                        if (bv < pv) {
                            if (++pos >= keys.length) break;
                            bv = keys[pos];
                            continue;
                        }
                        else if (bv == pv) {
                            assert currentBlockIdx < n;
                            vals[pos] = 1L + valuesOffset + currentBlockIdx * 8L;
                            if (++pos >= keys.length) break;
                            bv = keys[pos];
                            continue;
                        }

                        if (++currentBlockIdx < n) {
                            pv = page.getLong( dataOffset + currentBlockIdx * 8);
                        }
                        else {
                            break;
                        }
                    }
                }

                if (currentBlockIdx >= n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {

                        break;
                    }
                    currentBlockOffset = 0;
                    currentBlockIdx = 0;
                    if (pos >= keys.length) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                    }
                    else {
                        pageNo++;
                        long nextBlock = currentBlock + SkipListConstants.BLOCK_SIZE;
                        for (int i = 0; i < fc; i++) {
                            long max = ms.get(ValueLayout.JAVA_LONG, currentBlockOffset + SkipListConstants.HEADER_SIZE + 8L * i);
                            if (max < keys[pos]) {
                                nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * (SkipListConstants.skipOffsetForPointer(i) + 1);
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


    boolean rejectInPage(MemoryPage page, int dataOffset, int n, LongQueryBuffer data) {

        while (currentBlockIdx < n && data.hasMore()) {
            currentBlockIdx = page.binarySearchLong(data.currentValue(), dataOffset, currentBlockIdx, n);
            if (currentBlockIdx >= n) break;

            long pv = page.getLong( dataOffset + currentBlockIdx * 8);
            long bv = data.currentValue();

            if (pv == bv) {
                data.rejectAndAdvance();
                currentBlockIdx++;
                break;
            }
            else if (pv > bv) {
                data.retainAndAdvance();
            }
            else {
                currentBlockIdx ++;
            }
        }

        if (currentBlockIdx < n && data.hasMore()) {
            long pv = page.getLong( dataOffset + currentBlockIdx * 8);
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

                if (++currentBlockIdx < n) {
                    pv = page.getLong( dataOffset + currentBlockIdx * 8);
                }
                else {
                    return true;
                }
            }
        }

        return currentBlockIdx >= n;
    }

    public void rejectData(@NotNull LongQueryBuffer data) {
        while (data.hasMore()) {
            try (var page = pool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();

                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);

                if (rejectInPage(page, dataOffset, n, data)) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.retainAndAdvance();
                        break;
                    }
                    currentBlockOffset = 0;
                    currentBlockIdx = 0;
                    if (!data.hasMore()) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                    }
                    else {
                        long nextBlock = currentBlock + SkipListConstants.BLOCK_SIZE;
                        for (int i = 0; i < fc; i++) {
                            long max = ms.get(ValueLayout.JAVA_LONG, currentBlockOffset + SkipListConstants.HEADER_SIZE + 8L * i);
                            if (max < data.currentValue()) {
                                nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * (SkipListConstants.skipOffsetForPointer(i) + 1);
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
                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);

                if (n == 0) {
                    throw new IllegalStateException("Reading null memory!");
                }

                assert fc >= 0;
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);

                int nCopied = dest.addData(ms, dataOffset, n - currentBlockIdx);
                currentBlockIdx += nCopied;

                if (currentBlockIdx >= n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (!atEnd) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                }

                totalCopied += nCopied;
            }
        }

        return totalCopied;
    }


    public record RecordView(int n,
                             int fc,
                             int flags,
                             long docOffset,
                             LongList fowardPointers,
                             LongList docIds)
    {
        public long highestDocId() {
            return docIds.getLast();
        }
    }

    public static RecordView parseBlock(MemorySegment seg, int offset) {
        int n = headerNumRecords(seg, offset);
        int fc = headerForwardCount(seg, offset);
        int flags = headerFlags(seg, offset);

        assert n <= SkipListConstants.MAX_RECORDS_PER_BLOCK : "Invalid header, n = " + n;

        offset += SkipListConstants.HEADER_SIZE;

        LongList forwardPointers = new LongArrayList();
        for (int i = 0; i < fc; i++) {
            forwardPointers.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L*i));
        }
        offset += 8*fc;

        LongList docIds = new LongArrayList();
        long docOffset = seg.get(ValueLayout.JAVA_LONG, offset);
        offset += 8;

        long currentBlock = offset & -SkipListConstants.BLOCK_SIZE;
        long lastDataBlock = (offset + 8L * (n-1)) & - SkipListConstants.BLOCK_SIZE;

        if (currentBlock != lastDataBlock) {
            throw new IllegalStateException("Last data block is not the same as the current data block");
        }

        for (int i = 0; i < n; i++) {
            docIds.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L * i));
        }

        for (int i = 1; i < docIds.size(); i++) {
            if (docIds.getLong(i-1) >= docIds.getLong(i)) {
                throw new IllegalStateException("docIds are not increasing" + new RecordView(n, fc, flags, docOffset, forwardPointers, docIds));
            }
        }
        if ((docOffset & 7) != 0) {
            throw new IllegalStateException("docOffset is not long-aligned" + new RecordView(n, fc, flags, docOffset, forwardPointers, docIds));
        }

        return new RecordView(n, fc, flags, docOffset, forwardPointers, docIds);
    }

    public static List<RecordView> parseBlocks(MemorySegment seg, int offset) {
        List<RecordView> ret = new ArrayList<>();
        RecordView block;
        do {
            block = parseBlock(seg, offset);
            ret.add(block);
            offset = (offset + SkipListConstants.BLOCK_SIZE) & -SkipListConstants.BLOCK_SIZE;
        } while (0 == (block.flags & SkipListConstants.FLAG_END_BLOCK));

        return ret;
    }

    public static List<RecordView> parseBlocks(BufferPool pool, long offset) {
        List<RecordView> ret = new ArrayList<>();
        RecordView block;
        do {
            try (var page = pool.get(offset & -SkipListConstants.BLOCK_SIZE)) {
                block = parseBlock(page.getMemorySegment(), (int) (offset & (SkipListConstants.BLOCK_SIZE - 1)));
                ret.add(block);
                offset = (offset + SkipListConstants.BLOCK_SIZE) & -SkipListConstants.BLOCK_SIZE;
            }

        } while (0 == (block.flags & SkipListConstants.FLAG_END_BLOCK));

        return ret;
    }

    public static int headerNumRecords(MemoryPage buffer, int offset) {
        return buffer.getInt(offset);
    }

    public static int headerNumRecords(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_INT, offset);
    }

    public static int headerForwardCount(MemoryPage buffer, int offset) {
        return buffer.getByte(offset + 4);
    }

    public static int headerForwardCount(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_BYTE, offset + 4);
    }

    private long headerValuesBaseOffset(MemoryPage buffer, int blockOffset) {
        return buffer.getLong(blockOffset + 8 * (1+headerForwardCount(buffer, blockOffset)));
    }

    public static int headerFlags(MemoryPage buffer, int offset) {
        return buffer.getByte(offset + 5);
    }

    public static int headerFlags(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_BYTE, offset + 5);
    }

    public static int docIdsOffset(MemorySegment block, int offset) {
        return offset + SkipListConstants.HEADER_SIZE + 8 * headerForwardCount(block, offset);
    }

    public static int valuesOffset(MemorySegment block, int offset) {
        return offset + SkipListConstants.HEADER_SIZE + 8 * (headerForwardCount(block, offset) + headerNumRecords(block, offset));
    }

}
