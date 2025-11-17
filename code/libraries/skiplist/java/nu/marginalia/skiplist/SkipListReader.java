package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.array.pool.MemoryPage;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static nu.marginalia.skiplist.SkipListConstants.*;

public class SkipListReader {

    static final int BLOCK_STRIDE = BLOCK_SIZE;

    private static final boolean enableValuePrefetching = Boolean.getBoolean("index.enableValuePrefetching");
    private static final boolean enableIndexPrefetching = Boolean.getBoolean("index.enableIndexPrefetching");

    private final BufferPool indexPool;
    private final BufferPool valuesPool;

    private final long blockStart;

    private long currentBlock;
    private int currentBlockOffset;
    private int currentBlockIdx;

    private boolean atEnd;

    public int[] __stats_match_histo_retain = new int[512];
    public int[] __stats_match_histo_reject = new int[512];

    public int __stats__valueReads = 0;

    public SkipListReader(BufferPool indexPool, BufferPool valuesPool, long blockStart) {
        this.indexPool = indexPool;
        this.valuesPool = valuesPool;
        this.blockStart = blockStart;

        currentBlock = blockStart & -BLOCK_SIZE;
        currentBlockOffset = (int) (blockStart & (BLOCK_SIZE - 1));
        atEnd = false;

        if (enableIndexPrefetching) {
            indexPool.prefetch(currentBlock);
        }

        currentBlockIdx = 0;
    }

    /** Reset the index to the root block so that it can be re-used for additional operations. */
    public void reset() {
        currentBlock = blockStart & -BLOCK_SIZE;
        currentBlockOffset = (int) (blockStart & (BLOCK_SIZE - 1));
        currentBlockIdx = 0;

        atEnd = false;
    }

    public boolean atEnd() {
        return atEnd;
    }

    public int estimateSize() {
        try (var page = indexPool.get(currentBlock)) {
            int fc = headerForwardCount(page, currentBlockOffset);
            if (fc > 0) {
                return MAX_RECORDS_PER_BLOCK * skipOffsetForPointer(fc);
            }
            else {
                return headerNumRecords(page, currentBlockOffset);
            }
        }
    }

    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.  This operation will return after intersecting with
     * a single page, and return true if additional computation is available.
     */
    public boolean tryRetainData(@NotNull LongQueryBuffer data) {
        assert data.isAscending();

        try (var page = indexPool.get(currentBlock)) {

            int n = headerNumRecords(page, currentBlockOffset);
            int fc = headerForwardCount(page, currentBlockOffset);
            int flags = headerFlags(page, currentBlockOffset);

            int dataOffset = pageDataOffset(currentBlockOffset, fc);

            long maxVal = maxValueInBlock(page, fc, n);
            long targetValue = data.peekValueLt(maxVal);
            long nextBlock;

            if (targetValue > maxVal) {
                nextBlock = findNextBlock(page, fc, maxVal);
            }
            else {
                nextBlock = currentBlock + BLOCK_STRIDE;
            }

            if (enableIndexPrefetching && (flags & FLAG_END_BLOCK) == 0) {
                indexPool.prefetch(nextBlock);
            }

            if (data.currentValue() > maxVal || retainInPage(page, dataOffset, n, data)) {
                atEnd = (flags & FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.rejectAndAdvance();
                    return false;
                }

                currentBlockOffset = 0;
                currentBlockIdx = 0;
                currentBlock = nextBlock;
            }
        }

        return data.hasMore();
    }

    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.
     */
    public void retainData(@NotNull LongQueryBuffer data) {
        assert data.isAscending();

        while (tryRetainData(data));
    }

    boolean retainInPage(MemoryPage page, int dataOffset, int n, LongQueryBuffer data) {

        int matches = 0;

        while (data.hasMore()
                && n > (currentBlockIdx = page.binarySearchLong(data.currentValue(), dataOffset, currentBlockIdx, n)))
        {
            if (data.currentValue() != page.getLong( dataOffset + currentBlockIdx * 8)) {
                data.rejectAndAdvance();
            }
            else {
                data.retainAndAdvance();
                matches++;
                break;
            }
        }

        outer:
        while (data.hasMore()) {
            long bv = data.currentValue();

            for (; currentBlockIdx < n; currentBlockIdx++) {
                long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                if (bv < pv) {
                    data.rejectAndAdvance();
                    continue outer;
                }
                else if (bv == pv) {
                    data.retainAndAdvance();
                    matches++;
                    currentBlockIdx++;
                    continue outer;
                }
            }
            break;
        }

        __stats_match_histo_retain[Math.min(matches, __stats_match_histo_retain.length-1)]++;

        return currentBlockIdx >= n;
    }

    /** Gets all of the values associated with the keys provided as input.
     * Values that are not found in the skip list index are set to zero.
     *
     * To help with cache locality when utilizing the data, the values are
     * de-interleaved in the result array, so for a record size of 3,
     * the result array will look like [ 1, 2, 3, 4, ..., 1, 2, 3, 4, ... ]
     * */
    public long[] getAllValues(long[] keys) {
        int pos = 0;

        long[] vals = new long[keys.length * (RECORD_SIZE-1)];
        Arrays.fill(vals, 0, keys.length, -1L);

        if (getClass().desiredAssertionStatus()) {
            for (int i = 1; i < keys.length; i++) {
                assert keys[i] >= keys[i-1] : "Not ascending: " + Arrays.toString(keys);
            }
        }

        long lastValueBlock = -1;

        LongArrayFIFOQueue prefetchBlocks = enableValuePrefetching ? new LongArrayFIFOQueue() : null;

        while (pos < keys.length) {
            try (var page = indexPool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();
                assert ms.get(ValueLayout.JAVA_INT, currentBlockOffset) != 0 : "Likely reading zero space @ " + currentBlockOffset + " starting at " + blockStart + " -- " + parseBlock(ms, currentBlockOffset);
                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                long valuesOffset = headerValueOffset(page, currentBlockOffset);

                if (n == 0) {
                    throw new IllegalStateException("Reading null memory!");
                }

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                int remainingToRead = n - currentBlockIdx;
                if (remainingToRead <= 0)
                    break;

                int searchStart = currentBlockIdx;

                outer:
                while (pos < keys.length) {
                    long kv = keys[pos];

                    for (; currentBlockIdx < searchStart + remainingToRead; currentBlockIdx++) {
                        long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                        if (kv < pv) {
                            pos++;
                            continue outer;
                        }
                        else if (kv == pv) {
                            long val = valuesOffset + 8L * (currentBlockIdx - searchStart) * (RECORD_SIZE-1);
                            vals[pos] = val;

                            if (enableValuePrefetching) {
                                long valBlock = val & -VALUE_BLOCK_SIZE;
                                if (valBlock != lastValueBlock) {
                                    if (lastValueBlock < 0) { // prefetch the first block immediately
                                        valuesPool.prefetch(valBlock);
                                    }
                                    else {
                                        prefetchBlocks.enqueue(valBlock);
                                    }

                                    lastValueBlock = valBlock;

                                }
                            }

                            pos++;
                            continue outer;
                        }
                    }
                    break;
                }

                if (currentBlockIdx >= n) {
                    atEnd = (flags & FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        break;
                    }

                    if (pos >= keys.length) {
                        currentBlock += BLOCK_STRIDE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                    else {
                        long nextBlock = currentBlock + (long) BLOCK_STRIDE;
                        long currentValue = keys[pos];
                        for (int i = 0; i < fc; i++) {
                            long blockMaxValue = page.getLong(currentBlockOffset + DATA_BLOCK_HEADER_SIZE + 8 * i);
                            nextBlock = currentBlock + (long) BLOCK_STRIDE * skipOffsetForPointer(Math.max(0, i-1));
                            if (blockMaxValue >= currentValue) {
                                break;
                            }
                        }
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                        currentBlock = nextBlock;
                    }
                }
            }
        }

        int i = 0;
        while (i < keys.length) {
            if (vals[i] < 0) {
                vals[i] = 0;
                i++;
            }
            else {
                long valBlock = vals[i] & -VALUE_BLOCK_SIZE;

                if (enableValuePrefetching && !prefetchBlocks.isEmpty()) {
                    for (int prefetchIter = 0; prefetchIter < 2 && !prefetchBlocks.isEmpty(); prefetchIter++) {
                        valuesPool.prefetch(prefetchBlocks.dequeueLong());
                    }
                }

                try (var page = valuesPool.get(valBlock)) {

                    for (; i < keys.length; i++) {
                        if (vals[i] < 0) {
                            vals[i] = 0;
                        }
                        else {
                            if ((vals[i] & -VALUE_BLOCK_SIZE) != valBlock)
                                break;

                            int offsetBase = (int) (vals[i] & (VALUE_BLOCK_SIZE - 1));
                            for (int j = 0; j < RECORD_SIZE - 1; j++) {
                                vals[i + j * keys.length] = page.getLong(offsetBase + 8*j);
                            }
                        }
                    }
                }
            }
        }
        return vals;
    }

    public BitSet getAllPresentValues(long[] keys) {
        int pos = 0;
        BitSet ret = new BitSet(keys.length);

        if (getClass().desiredAssertionStatus()) {
            for (int i = 1; i < keys.length; i++) {
                assert keys[i] >= keys[i-1] : "Not ascending: " + Arrays.toString(keys);
            }
        }

        while (pos < keys.length) {
            try (var page = indexPool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();
                assert ms.get(ValueLayout.JAVA_INT, currentBlockOffset) != 0 : "Likely reading zero space @ " + currentBlockOffset + " starting at " + blockStart + " -- " + parseBlock(ms, currentBlockOffset);
                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                if (n == 0) {
                    throw new IllegalStateException("Reading null memory!");
                }

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                int remainingToRead = n - currentBlockIdx;
                if (remainingToRead <= 0)
                    break;

                int searchStart = currentBlockIdx;

                outer:
                while (pos < keys.length) {
                    long kv = keys[pos];

                    for (; currentBlockIdx < searchStart + remainingToRead; currentBlockIdx++) {
                        long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                        if (kv < pv) {
                            pos++;
                            continue outer;
                        }
                        else if (kv == pv) {
                            ret.set(pos);
                            pos++;
                            continue outer;
                        }
                    }
                    break;
                }

                if (currentBlockIdx >= n) {
                    atEnd = (flags & FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        break;
                    }

                    if (pos >= keys.length) {
                        currentBlock += BLOCK_STRIDE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                    else {
                        long nextBlock = currentBlock + (long) BLOCK_STRIDE;
                        long currentValue = keys[pos];
                        for (int i = 0; i < fc; i++) {
                            long blockMaxValue = page.getLong(currentBlockOffset + DATA_BLOCK_HEADER_SIZE + 8 * i);
                            nextBlock = currentBlock + (long) BLOCK_STRIDE * skipOffsetForPointer(Math.max(0, i-1));
                            if (blockMaxValue >= currentValue) {
                                break;
                            }
                        }
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                        currentBlock = nextBlock;
                    }
                }
            }
        }

        return ret;
    }

    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.  This operation will return after intersecting with
     * a single page, and return true if additional computation is available.
     */
    public boolean tryRejectData(@NotNull LongQueryBuffer data) {
        assert data.isAscending();

        assert data.isAscending();

        try (var page = indexPool.get(currentBlock)) {

            int n = headerNumRecords(page, currentBlockOffset);
            int fc = headerForwardCount(page, currentBlockOffset);
            int flags = headerFlags(page, currentBlockOffset);

            int dataOffset = pageDataOffset(currentBlockOffset, fc);

            long maxVal = maxValueInBlock(page, fc, n);
            long targetValue = data.peekValueLt(maxVal);
            long nextBlock;

            if (targetValue > maxVal) {
                nextBlock = findNextBlock(page, fc, maxVal);
            }
            else {
                nextBlock = currentBlock + BLOCK_STRIDE;
            }

            if (enableIndexPrefetching && (flags & FLAG_END_BLOCK) == 0) {
                indexPool.prefetch(nextBlock);
            }

            if (data.currentValue() > maxVal || rejectInPage(page, dataOffset, n, data)) {
                atEnd = (flags & FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.retainAndAdvance();
                    return false;
                }

                currentBlockOffset = 0;
                currentBlockIdx = 0;
                currentBlock = nextBlock;
            }
        }

        return data.hasMore();
    }

    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.
     */
    public void rejectData(@NotNull LongQueryBuffer data) {
        while (tryRejectData(data));
    }

    boolean rejectInPage(MemoryPage page, int dataOffset, int n, LongQueryBuffer data) {

        int matches = 0;

        while (data.hasMore()
                && n > (currentBlockIdx = page.binarySearchLong(data.currentValue(), dataOffset, currentBlockIdx, n)))
        {
            if (data.currentValue() != page.getLong( dataOffset + currentBlockIdx * 8)) {
                data.retainAndAdvance();
            }
            else {
                data.rejectAndAdvance();
                matches++;
                break;
            }
        }

        outer:
        while (data.hasMore()) {
            long bv = data.currentValue();

            for (; currentBlockIdx < n; currentBlockIdx++) {
                long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                if (bv < pv) {
                    data.retainAndAdvance();
                    continue outer;
                }
                else if (bv == pv) {
                    data.rejectAndAdvance();
                    matches++;
                    currentBlockIdx++;
                    continue outer;
                }
            }
            break;
        }

        __stats_match_histo_reject[Math.min(matches, __stats_match_histo_reject.length-1)]++;
        return currentBlockIdx >= n;
    }

    /** Fills the buffer with keys from the index.  The caller should use
     * atEnd() to decide when the index has been exhausted.
     *
     * @return the number of items added to the index
     * */
    public int getKeys(@NotNull LongQueryBuffer dest)
    {
        if (atEnd) return 0;
        assert dest.isAscending();

        int totalCopied = 0;
        while (dest.fitsMore() && !atEnd) {
            try (var page = indexPool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();

                assert ms.get(ValueLayout.JAVA_INT, currentBlockOffset) != 0 : "Likely reading zero space";
                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);

                if (n == 0) {
                    throw new IllegalStateException("Reading null memory!");
                }

                assert fc >= 0;
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                if (enableIndexPrefetching && (flags & FLAG_END_BLOCK) == 0) {
                    indexPool.prefetch(currentBlock + BLOCK_STRIDE);
                }

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                int nCopied = dest.addData(ms, dataOffset + currentBlockIdx * 8L, n - currentBlockIdx);
                currentBlockIdx += nCopied;
                totalCopied += nCopied;

                if (currentBlockIdx >= n) {
                    atEnd = (flags & FLAG_END_BLOCK) != 0;
                    if (!atEnd) {
                        currentBlock += BLOCK_STRIDE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                }

            }
        }

        return totalCopied;
    }


    /** Fills the buffer with keys from the index.  The caller should use
     * atEnd() to decide when the index has been exhausted.
     *
     * @return the number of items added to the index
     * */
    public int getKeys(@NotNull LongQueryBuffer dest, @NotNull SkipListValueRanges ranges)
    {
        if (atEnd) return 0;
        assert dest.isAscending();

        int totalCopied = 0;
        outer:
        while (dest.fitsMore() && !atEnd && !ranges.atEnd()) {
            try (var page = indexPool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();

                assert ms.get(ValueLayout.JAVA_INT, currentBlockOffset) != 0 : "Likely reading zero space";
                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);

                if (n == 0) {
                    throw new IllegalStateException("Reading null memory!");
                }

                assert fc >= 0;
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                long blockMinValue = ms.get(ValueLayout.JAVA_LONG, dataOffset);
                boolean inRange = false;

                do {
                    long rangeEnd;
                    while ((rangeEnd = ranges.end()) < blockMinValue) {
                        if (!ranges.next()) break outer;
                    }

                    long rangeStart = ranges.start();

                    int dataStart = page.binarySearchLong(rangeStart, dataOffset, 0, n);

                    if (dataStart == n) {
                        break;
                    }

                    int dataEnd = page.binarySearchLong(rangeEnd, dataOffset, dataStart, n);
                    if (dataStart != dataEnd) {
                        totalCopied += dest.addData(ms, dataOffset + dataStart * 8, (dataEnd - dataStart));
                        if (dataEnd == n) {
                            inRange = true;
                            break;
                        }
                    }
                } while (ranges.next());

                atEnd = (flags & FLAG_END_BLOCK) != 0 || ranges.atEnd();

                if (atEnd)
                    break;

                long nextBlock = currentBlock + (long) BLOCK_STRIDE;
                long currentValue = ranges.start();

                if (!inRange) {
                    for (int i = 0; i < fc; i++) {
                        long nextBlockMaxValue = page.getLong(currentBlockOffset + DATA_BLOCK_HEADER_SIZE + 8 * i);
                        nextBlock = currentBlock + (long) BLOCK_STRIDE * skipOffsetForPointer(Math.max(0, i - 1));
                        if (nextBlockMaxValue >= currentValue) {
                            break;
                        }
                    }
                }

                currentBlockOffset = 0;
                currentBlockIdx = 0;
                currentBlock = nextBlock;
            }
        }

        return totalCopied;
    }

    /** Return the last (and largest) value in this page */
    private long maxValueInBlock(MemoryPage page, int fc, int n) {
        return page.getLong(pageDataOffset(currentBlockOffset, fc) + 8*(n-1));
    }

    /** Return the next block we need to look in if we are looking for targetValue */
    private long findNextBlock(MemoryPage page, int fc, long targetValue) {
        long nextBlock = currentBlock + (long) BLOCK_STRIDE;
        for (int i = 0; i < fc; i++) {
            long blockMaxValue = page.getLong(currentBlockOffset + DATA_BLOCK_HEADER_SIZE + 8 * i);
            nextBlock = currentBlock + (long) BLOCK_STRIDE * skipOffsetForPointer(Math.max(0, i-1));
            if (blockMaxValue >= targetValue) {
                break;
            }
        }
        return nextBlock;
    }


    public record RecordView(int n,
                             int fc,
                             int flags,
                             LongList fowardPointers,
                             LongList docIds,
                             long segmentOffset,
                             long valuesOffset
                             )
    {
        public long highestDocId() {
            return docIds.getLast();
        }
    }

    public static RecordView parseBlock(MemorySegment seg, long offset) {
        int n = headerNumRecords(seg, (int) offset);
        int fc = headerForwardCount(seg, (int) offset);
        int flags = headerFlags(seg, (int) offset);
        long valueOffset = headerValueOffset(seg, (int) offset);
        long recordOffset = offset;

        assert n <= MAX_RECORDS_PER_BLOCK : "Invalid header, n = " + n;
        assert (flags & FLAG_VALUE_BLOCK) == 0 : "Attempting to parse value block";

        offset += DATA_BLOCK_HEADER_SIZE;

        LongList forwardPointers = new LongArrayList();
        for (int i = 0; i < fc; i++) {
            forwardPointers.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L*i));
        }
        offset += 8*fc;

        LongList docIds = new LongArrayList();

        long currentBlock = offset & -BLOCK_SIZE;
        long lastDataBlock = (offset + 8L * (n-1)) & - BLOCK_SIZE;

        if (currentBlock != lastDataBlock) {
            throw new IllegalStateException("Last data block is not the same as the current data block (n=" + n +", flags=" + flags + ")" + " for block offset " + (offset & (BLOCK_SIZE - 1)));
        }

        for (int i = 0; i < n; i++) {
            docIds.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L * i));
        }

        for (int i = 1; i < docIds.size(); i++) {
            if (docIds.getLong(i-1) >= docIds.getLong(i)) {
                throw new IllegalStateException("docIds are not increasing" + new RecordView(n, fc, flags, forwardPointers, docIds, recordOffset, valueOffset));
            }
        }

        if ((valueOffset & 7) != 0) {
            throw new IllegalStateException("Value offset is not a multiple of 8: " +  new RecordView(n, fc, flags, forwardPointers, docIds, recordOffset, valueOffset));
        }


        return new RecordView(n, fc, flags, forwardPointers, docIds, recordOffset, valueOffset);
    }

    public static List<RecordView> parseBlocks(MemorySegment seg, long offset) {
        List<RecordView> ret = new ArrayList<>();
        RecordView block;
        do {
            System.out.println((offset & -BLOCK_SIZE) + ":" + (offset & (BLOCK_SIZE-1)));
            block = parseBlock(seg, offset);
            System.out.println(block);
            ret.add(block);
            offset = (offset + BLOCK_SIZE) & -BLOCK_SIZE;
        } while (0 == (block.flags & FLAG_END_BLOCK));

        return ret;
    }

    public static List<RecordView> parseBlocks(BufferPool pool, long offset) {
        List<RecordView> ret = new ArrayList<>();
        RecordView block;
        do {
            try (var page = pool.get(offset & -BLOCK_SIZE)) {
                block = parseBlock(page.getMemorySegment(), (int) (offset & (BLOCK_SIZE - 1)));
                ret.add(block);
                offset = (offset + BLOCK_SIZE) & -BLOCK_SIZE;
            }

        } while (0 == (block.flags & FLAG_END_BLOCK));

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

    public static int headerFlags(MemoryPage buffer, int offset) {
        return buffer.getByte(offset + 5);
    }

    public static int headerFlags(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_BYTE, offset + 5);
    }

    public static long headerValueOffset(MemoryPage block, int offset) {
        return block.getLong(offset + 8);
    }

    public static long headerValueOffset(MemorySegment block, int offset) {
        return block.get(ValueLayout.JAVA_LONG, offset + 8);
    }

    public static int docIdsOffset(MemorySegment block, int offset) {
        return offset + DATA_BLOCK_HEADER_SIZE + 8 * headerForwardCount(block, offset);
    }

    public static int valuesOffset(MemorySegment block, int offset) {
        return offset + DATA_BLOCK_HEADER_SIZE + 8 * (headerForwardCount(block, offset) + headerNumRecords(block, offset));
    }

}
