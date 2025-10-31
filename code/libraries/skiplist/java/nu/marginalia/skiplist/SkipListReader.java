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

    /** Reset the index to the root block so that it can be re-used for additional operations. */
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

            int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);
            if (retainInPage(page, dataOffset, n, data)) {
                atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.rejectAndAdvance();
                    return false;
                }

                if (!data.hasMore()) {
                    currentBlock += SkipListConstants.BLOCK_SIZE;
                    currentBlockOffset = 0;
                    currentBlockIdx = 0;
                }
                else {
                    long nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE;
                    long currentValue = data.currentValue();
                    for (int i = 0; i < fc; i++) {
                        long blockMaxValue = page.getLong(currentBlockOffset + SkipListConstants.HEADER_SIZE + 8 * i);
                        nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * SkipListConstants.skipOffsetForPointer(Math.max(0, i-1));
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

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);
                if (retainInPage(page, dataOffset, n, data)) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.rejectAndAdvance();
                        return;
                    }

                    if (!data.hasMore()) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                    else {
                        long nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE;
                        long currentValue = data.currentValue();
                        for (int i = 0; i < fc; i++) {
                            long blockMaxValue = page.getLong(currentBlockOffset + SkipListConstants.HEADER_SIZE + 8 * i);
                            nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * SkipListConstants.skipOffsetForPointer(Math.max(0, i-1));
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
                    currentBlockIdx++;
                    continue outer;
                }
            }
            break;
        }

        return currentBlockIdx >= n;
    }


    /** Gets the values associated with the keys provided as input.
     * Values that are not found in the skip list index are set to zero.
     * */
    public long[] getValues(long[] keys, int valueIdx) {
        int pos = 0;

        final int valueRecordSize = (SkipListConstants.RECORD_SIZE-1);

        long[] vals = new long[keys.length];

        if (valueIdx < 0 || valueIdx >= SkipListConstants.RECORD_SIZE)
            throw new IllegalArgumentException("Bad valueIdx: " + valueIdx);

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

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);
                int valuesOffset = dataOffset + 8 * n;
                if ((valuesOffset & 7) != 0) {
                    throw new IllegalStateException(parseBlock(ms, currentBlockOffset).toString());
                }

                int matches = 0;

                while (pos < keys.length
                        && n > (currentBlockIdx = page.binarySearchLong(keys[pos], dataOffset, currentBlockIdx, n)))
                {
                    if (keys[pos] != page.getLong( dataOffset + currentBlockIdx * 8)) {
                        pos++;
                    }
                    else {
                        vals[pos++] = page.getLong(valuesOffset + (valueRecordSize * currentBlockIdx + valueIdx) * 8);
                        matches++;

                        if (++matches > 5) {
                            break;
                        }
                    }
                }

                outer:
                while (pos < keys.length) {
                    long kv = keys[pos];

                    for (; currentBlockIdx < n; currentBlockIdx++) {
                        long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                        if (kv < pv) {
                            pos++;
                            continue outer;
                        }
                        else if (kv == pv) {
                            vals[pos++] = page.getLong(valuesOffset + (valueRecordSize * currentBlockIdx + valueIdx) * 8);
                            continue outer;
                        }
                    }
                    break;
                }

                if (currentBlockIdx >= n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        break;
                    }

                    if (pos >= keys.length) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                    else {
                        long nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE;
                        long currentValue = keys[pos];
                        for (int i = 0; i < fc; i++) {
                            long blockMaxValue = page.getLong(currentBlockOffset + SkipListConstants.HEADER_SIZE + 8 * i);
                            nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * SkipListConstants.skipOffsetForPointer(Math.max(0, i-1));
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

    /** Gets all of the values associated with the keys provided as input.
     * Values that are not found in the skip list index are set to zero.
     *
     * To help with cache locality when utilizing the data, the values are
     * de-interleaved in the result array, so for a record size of 3,
     * the result array will look like [ 1, 2, 3, 4, ..., 1, 2, 3, 4, ... ]
     * */
    public long[] getAllValues(long[] keys) {
        int pos = 0;

        final int valueRecordSize = (SkipListConstants.RECORD_SIZE-1);

        long[] vals = new long[keys.length * valueRecordSize];

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

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);
                int valuesOffset = dataOffset + 8 * n;
                if ((valuesOffset & 7) != 0) {
                    throw new IllegalStateException(parseBlock(ms, currentBlockOffset).toString());
                }

                int matches = 0;

                while (pos < keys.length
                        && n > (currentBlockIdx = page.binarySearchLong(keys[pos], dataOffset, currentBlockIdx, n)))
                {
                    if (keys[pos] != page.getLong( dataOffset + currentBlockIdx * 8)) {
                        pos++;
                    }
                    else {
                        for (int i = 0; i < valueRecordSize; i++) {
                            vals[i * keys.length + pos] = page.getLong(valuesOffset + (valueRecordSize * currentBlockIdx + i) * 8);
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

                    for (; currentBlockIdx < n; currentBlockIdx++) {
                        long pv = page.getLong( dataOffset + currentBlockIdx * 8);
                        if (kv < pv) {
                            pos++;
                            continue outer;
                        }
                        else if (kv == pv) {
                            for (int i = 0; i < valueRecordSize; i++) {
                                vals[i * keys.length + pos] = page.getLong(valuesOffset + (valueRecordSize * currentBlockIdx + i) * 8);
                            }
                            pos++;
                            continue outer;
                        }
                    }
                    break;
                }

                if (currentBlockIdx >= n) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        break;
                    }

                    if (pos >= keys.length) {
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                    }
                    else {
                        long nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE;
                        long currentValue = keys[pos];
                        for (int i = 0; i < fc; i++) {
                            long blockMaxValue = page.getLong(currentBlockOffset + SkipListConstants.HEADER_SIZE + 8 * i);
                            nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * SkipListConstants.skipOffsetForPointer(Math.max(0, i-1));
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

            int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);
            if (rejectInPage(page, dataOffset, n, data)) {
                atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.retainAndAdvance();
                    return false;
                }

                if (!data.hasMore()) {
                    currentBlockOffset = 0;
                    currentBlockIdx = 0;
                    currentBlock += SkipListConstants.BLOCK_SIZE;
                }
                else {
                    long nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE;
                    long currentValue = data.currentValue();
                    for (int i = 0; i < fc; i++) {
                        long blockMaxValue = page.getLong(currentBlockOffset + SkipListConstants.HEADER_SIZE + 8 * i);
                        nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * SkipListConstants.skipOffsetForPointer(Math.max(0, i-1));
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

                int dataOffset = SkipListConstants.pageDataOffset(currentBlockOffset, fc);

                if (rejectInPage(page, dataOffset, n, data)) {
                    atEnd = (flags & SkipListConstants.FLAG_END_BLOCK) != 0;
                    if (atEnd) {
                        while (data.hasMore())
                            data.retainAndAdvance();
                        break;
                    }
                    if (!data.hasMore()) {
                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                        currentBlock += SkipListConstants.BLOCK_SIZE;
                    }
                    else {
                        long nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE;
                        long currentValue = data.currentValue();
                        for (int i = 0; i < fc; i++) {
                            long blockMaxValue = page.getLong(currentBlockOffset + SkipListConstants.HEADER_SIZE + 8 * i);
                            nextBlock = currentBlock + (long) SkipListConstants.BLOCK_SIZE * SkipListConstants.skipOffsetForPointer(Math.max(0, i-1));
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
                    currentBlockIdx++;
                    continue outer;
                }
            }
            break;
        }

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

        long currentBlock = offset & -SkipListConstants.BLOCK_SIZE;
        long lastDataBlock = (offset + 8L * (n-1)) & - SkipListConstants.BLOCK_SIZE;

        if (currentBlock != lastDataBlock) {
            throw new IllegalStateException("Last data block is not the same as the current data block (n=" + n +", flags=" + flags + ")" + " for block offset " + (offset & (SkipListConstants.BLOCK_SIZE - 1)));
        }

        for (int i = 0; i < n; i++) {
            docIds.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L * i));
        }

        for (int i = 1; i < docIds.size(); i++) {
            if (docIds.getLong(i-1) >= docIds.getLong(i)) {
                throw new IllegalStateException("docIds are not increasing" + new RecordView(n, fc, flags, forwardPointers, docIds));
            }
        }


        return new RecordView(n, fc, flags, forwardPointers, docIds);
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
