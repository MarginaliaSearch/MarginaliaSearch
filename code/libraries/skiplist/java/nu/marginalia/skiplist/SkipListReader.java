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
import java.util.Arrays;
import java.util.List;

import static nu.marginalia.skiplist.SkipListConstants.*;

public class SkipListReader {

    static final int BLOCK_STRIDE = RECORD_SIZE * BLOCK_SIZE;

    private final BufferPool pool;
    private final long blockStart;

    private long currentBlock;
    private int currentBlockOffset;
    private int currentBlockIdx;

    private boolean atEnd;

    public int[] __stats_match_histo_retain = new int[512];
    public int[] __stats_match_histo_reject = new int[512];

    public int __stats__valueReads = 0;

    public SkipListReader(BufferPool pool, long blockStart) {
        this.pool = pool;
        this.blockStart = blockStart;

        currentBlock = blockStart & -BLOCK_SIZE;
        currentBlockOffset = (int) (blockStart & (BLOCK_SIZE - 1));
        atEnd = false;

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
        try (var page = pool.get(currentBlock)) {
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

        try (var page = pool.get(currentBlock)) {

            int n = headerNumRecords(page, currentBlockOffset);
            int fc = headerForwardCount(page, currentBlockOffset);
            int flags = headerFlags(page, currentBlockOffset);

            int dataOffset = pageDataOffset(currentBlockOffset, fc);
            if (retainInPage(page, dataOffset, n, data)) {
                atEnd = (flags & FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.rejectAndAdvance();
                    return false;
                }

                if (!data.hasMore()) {
                    currentBlock += BLOCK_STRIDE;
                    currentBlockOffset = 0;
                    currentBlockIdx = 0;
                }
                else {
                    long nextBlock = currentBlock + (long) BLOCK_STRIDE;
                    long currentValue = data.currentValue();
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

        return data.hasMore();
    }

    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.
     */
    public void retainData(@NotNull LongQueryBuffer data) {
        assert data.isAscending();

        while (data.hasMore()) {
            try (var page = pool.get(currentBlock)) {

                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);
                int flags = headerFlags(page, currentBlockOffset);

                int dataOffset = pageDataOffset(currentBlockOffset, fc);
                if (retainInPage(page, dataOffset, n, data)) {
                    atEnd = (flags & FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.rejectAndAdvance();
                        return;
                    }

                    if (!data.hasMore()) {
                        currentBlock += BLOCK_STRIDE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                    else {
                        long nextBlock = currentBlock + (long) BLOCK_STRIDE;
                        long currentValue = data.currentValue();
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

                if (++matches > 5) {
                    break;
                }
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

        if (getClass().desiredAssertionStatus()) {
            for (int i = 1; i < keys.length; i++) {
                assert keys[i] >= keys[i-1] : "Not ascending: " + Arrays.toString(keys);
            }
        }

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

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                if ((flags & FLAG_COMPACT_BLOCK) != 0) {
                    int valuesOffset = dataOffset + 8 * n + VALUE_BLOCK_HEADER_SIZE;
                    if ((valuesOffset & 7) != 0) {
                        throw new IllegalStateException(parseBlock(ms, currentBlockOffset).toString());
                    }
                    assert valuesOffset + 8*n*(RECORD_SIZE-1) <= ms.byteSize() : "This won't fit";

                    // For compact blocks, the values are in the same block as the keys
                    pos += copyValuesInPage(n, n, pos, keys, vals, page, dataOffset, page, valuesOffset);
                }
                else {
                    int valsPerBlock = (BLOCK_SIZE - VALUE_BLOCK_HEADER_SIZE) / (8 * (RECORD_SIZE-1));

                    for (int i = 1; i < RECORD_SIZE; i++) {
                        int remainingToRead = Math.min(n - currentBlockIdx, valsPerBlock);
                        if (remainingToRead <= 0)
                            break;

                        if (pos >= keys.length)
                            break;

                        long minValue = page.getLong(dataOffset + currentBlockIdx*8);
                        long maxValue = page.getLong(dataOffset + (currentBlockIdx + remainingToRead - 1)*8);

                        // Check if we can skip processing this block
                        if (keys[pos] > maxValue) {
                            currentBlockIdx += remainingToRead;
                            continue;
                        }
                        if (keys[keys.length-1] < minValue) {
                            currentBlockIdx = n;
                            pos = keys.length;
                            break;
                        }

                        try (var valuePage = pool.get(currentBlock + BLOCK_SIZE*i)) {
                            __stats__valueReads++;
                            pos = copyValuesInPage(n, remainingToRead, pos, keys, vals, page, dataOffset, valuePage, VALUE_BLOCK_HEADER_SIZE);
                        }
                    }
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

        return vals;
    }

    private int copyValuesInPage(int nTotal,
                                 int nBlock,
                                 int pos,
                                 long[] keys,
                                 long[] vals,
                                 MemoryPage keysPage,
                                 int dataOffset,
                                 MemoryPage valuesPage,
                                 int valuesOffset) {

        int matches = 0;
        final int valueRecordSize = 2;

        int searchStart = currentBlockIdx;
        while (pos < keys.length
                && Math.min(nTotal, searchStart+nBlock) > (currentBlockIdx = keysPage.binarySearchLong(keys[pos], dataOffset, currentBlockIdx, searchStart+nBlock)))
        {
            if (keys[pos] != keysPage.getLong( dataOffset + currentBlockIdx * 8)) {
                pos++;
            }
            else {
                int relativePosInBlock = currentBlockIdx - searchStart;
                for (int i = 0; i < valueRecordSize; i++) {
                    vals[i * keys.length + pos] = valuesPage.getLong(valuesOffset + (valueRecordSize * relativePosInBlock + i) * 8);
                }
                pos++;
                matches++;

                if (++matches > 5) {
                    break;
                }
            }
        }

        outer:
        while (pos < keys.length) {
            long kv = keys[pos];

            for (; currentBlockIdx < searchStart + nBlock; currentBlockIdx++) {
                long pv = keysPage.getLong( dataOffset + currentBlockIdx * 8);
                if (kv < pv) {
                    pos++;
                    continue outer;
                }
                else if (kv == pv) {
                    int relativePosInBlock = currentBlockIdx - searchStart;
                    for (int i = 0; i < valueRecordSize; i++) {
                        vals[i * keys.length + pos] = valuesPage.getLong(valuesOffset + (valueRecordSize * relativePosInBlock + i) * 8);
                    }
                    pos++;
                    continue outer;
                }
            }
            break;
        }

        return pos;
    }


    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.  This operation will return after intersecting with
     * a single page, and return true if additional computation is available.
     */
    public boolean tryRejectData(@NotNull LongQueryBuffer data) {
        assert data.isAscending();

        try (var page = pool.get(currentBlock)) {

            int n = headerNumRecords(page, currentBlockOffset);
            int fc = headerForwardCount(page, currentBlockOffset);
            int flags = headerFlags(page, currentBlockOffset);

            int dataOffset = pageDataOffset(currentBlockOffset, fc);
            if (rejectInPage(page, dataOffset, n, data)) {
                atEnd = (flags & FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.retainAndAdvance();
                    return false;
                }

                if (!data.hasMore()) {
                    currentBlockOffset = 0;
                    currentBlockIdx = 0;
                    currentBlock += BLOCK_STRIDE;
                }
                else {
                    long nextBlock = currentBlock + (long) BLOCK_STRIDE;
                    long currentValue = data.currentValue();
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

        return data.hasMore();
    }

    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.
     */
    public void rejectData(@NotNull LongQueryBuffer data) {
        while (data.hasMore()) {
            try (var page = pool.get(currentBlock)) {
                MemorySegment ms = page.getMemorySegment();

                int n = headerNumRecords(page, currentBlockOffset);
                int fc = headerForwardCount(page, currentBlockOffset);
                byte flags = (byte) headerFlags(page, currentBlockOffset);

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                if (rejectInPage(page, dataOffset, n, data)) {
                    atEnd = (flags & FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.retainAndAdvance();
                        break;
                    }
                    if (!data.hasMore()) {
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                        currentBlock += BLOCK_STRIDE;
                    }
                    else {
                        long nextBlock = currentBlock + (long) BLOCK_STRIDE;
                        long currentValue = data.currentValue();
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

                if (++matches > 5) {
                    break;
                }
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

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                int nCopied = dest.addData(ms, dataOffset + currentBlockIdx * 8, n - currentBlockIdx);
                currentBlockIdx += nCopied;

                if (currentBlockIdx >= n) {
                    atEnd = (flags & FLAG_END_BLOCK) != 0;
                    if (!atEnd) {
                        currentBlock += BLOCK_STRIDE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                }

                totalCopied += nCopied;
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

                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                long blockMinValue = ms.get(ValueLayout.JAVA_LONG, dataOffset);
                long blockMaxValue = ms.get(ValueLayout.JAVA_LONG, dataOffset + (n-1) * 8);
                boolean inRange = false;

                do {
                    long rangeEnd;
                    while ((rangeEnd = ranges.end()) < blockMinValue) {
                        if (!ranges.next()) break outer;
                    }

                    long rangeStart = ranges.start();

                    int dataStart = page.binarySearchLong(rangeStart, dataOffset, 0, n);
                    int dataEnd = page.binarySearchLong(rangeEnd, dataOffset, dataStart, n);

                    if (dataStart == n) {
                        break;
                    }

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


    public record RecordView(int n,
                             int fc,
                             int flags,
                             LongList fowardPointers,
                             LongList docIds,
                             long offset
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
                throw new IllegalStateException("docIds are not increasing" + new RecordView(n, fc, flags, forwardPointers, docIds, recordOffset));
            }
        }


        return new RecordView(n, fc, flags, forwardPointers, docIds, recordOffset);
    }

    public static List<RecordView> parseBlocks(MemorySegment seg, long offset) {
        List<RecordView> ret = new ArrayList<>();
        RecordView block;
        do {
            System.out.println((offset & -BLOCK_SIZE) + ":" + (offset & (BLOCK_SIZE-1)));
            block = parseBlock(seg, offset);
            System.out.println(block);
            ret.add(block);
            offset = (offset + RECORD_SIZE* BLOCK_SIZE) & -BLOCK_SIZE;
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
                offset = (offset + RECORD_SIZE* BLOCK_SIZE) & -BLOCK_SIZE;
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
        return offset + DATA_BLOCK_HEADER_SIZE + 8 * headerForwardCount(block, offset);
    }

    public static int valuesOffset(MemorySegment block, int offset) {
        return offset + DATA_BLOCK_HEADER_SIZE + 8 * (headerForwardCount(block, offset) + headerNumRecords(block, offset));
    }

}
