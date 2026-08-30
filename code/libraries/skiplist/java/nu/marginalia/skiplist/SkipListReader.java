package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.array.pool.MemoryPage;
import nu.marginalia.ffi.NativeAlgos;
import nu.marginalia.skiplist.compression.DocIdCompressor;
import nu.marginalia.skiplist.compression.output.SegmentCompressorBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static nu.marginalia.skiplist.SkipListConstants.*;

public class SkipListReader {

    /** Block readahead when there is weak evidence of a sequential pattern, should be ∈{0,1} probably */
    public static final int BLOCK_READ_AHEAD_MIN = Integer.getInteger("index.blockReadAheadMin", 1);

    /** Block readahead when we have indications of a sequential read pattern */
    public static final int BLOCK_READ_AHEAD_MAX = Integer.getInteger("index.blockReadAheadMax", 8);

    static final int BLOCK_STRIDE = BLOCK_SIZE;

    private final BufferPool indexPool;
    private final SkipListValueReader valuesReader;
    private final SkipListFormat format;

    private final long blockStart;

    private long currentBlock;
    private int currentBlockOffset;
    private int currentBlockIdx;

    private boolean atEnd;

    private int sequentialReadsObserved;

    private static final int DECOMPRESSED_BLOCK_POOL_SIZE = 128;

    private static final AtomicLong readerSequence = new AtomicLong();

    private static final class DecompressedBlock {
        public final long[] data = new long[BLOCK_SIZE];
        private final DecompressedBlockPool pool;

        private long ownerId = -1;
        private long block = -1;

        DecompressedBlock(DecompressedBlockPool pool) {
            this.pool = pool;
        }
    }

    private static final class DecompressedBlockPool {
        private final DecompressedBlock[] blocks = new DecompressedBlock[DECOMPRESSED_BLOCK_POOL_SIZE];
        private int next = 0;

        DecompressedBlock claim(long readerId) {
            DecompressedBlock scratch = blocks[next];
            if (scratch == null) {
                scratch = new DecompressedBlock(this);
                blocks[next] = scratch;
            }
            next = (next + 1) % blocks.length;

            scratch.ownerId = readerId;
            scratch.block = -1;

            return scratch;
        }
    }

    private static final ThreadLocal<DecompressedBlockPool> decompressedBlockPool = ThreadLocal.withInitial(DecompressedBlockPool::new);

    private final long readerId = readerSequence.incrementAndGet();
    private DecompressedBlock decompressedBlock;

    private long[] decompressBlock(MemoryPage page, int dataOffset, int n) {
        DecompressedBlockPool pool = decompressedBlockPool.get();
        DecompressedBlock scratch = decompressedBlock;

        if (scratch == null || scratch.ownerId != readerId || scratch.pool != pool) {
            scratch = pool.claim(readerId);
            decompressedBlock = scratch;
        }

        if (scratch.block != currentBlock) {
            DocIdCompressor.decompress(
                    new SegmentCompressorBuffer(page.getMemorySegment(), dataOffset),
                    n,
                    scratch.data);
            scratch.block = currentBlock;
        }

        return scratch.data;
    }

    public SkipListReader(BufferPool indexPool,
                          SkipListValueReader valuesReader,
                          long blockStart) {
        this(indexPool, valuesReader, blockStart, SkipListFormat.CURRENT);
    }

    public SkipListReader(BufferPool indexPool,
                          SkipListValueReader valuesReader,
                          long blockStart,
                          SkipListFormat format) {
        this.indexPool = indexPool;
        this.valuesReader = valuesReader;
        this.format = format;
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
        sequentialReadsObserved = 0;

        atEnd = false;
    }

    public boolean atEnd() {
        return atEnd;
    }

    public int estimateSize() {
        try (var page = indexPool.get(currentBlock)) {
            int fc = headerForwardCount(page, currentBlockOffset);
            if (fc > 0) {
                return MAX_RECORDS_PER_BLOCK * format.skipOffsetForPointer(fc);
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

        if (atEnd) return false;
        if (!data.hasMore()) return false;

        try (var page = indexPool.get(currentBlock, readAhead())) {

            int n = headerNumRecords(page, currentBlockOffset);
            int fc = headerForwardCount(page, currentBlockOffset);
            int flags = headerFlags(page, currentBlockOffset);

            int dataOffset = pageDataOffset(currentBlockOffset, fc);

            long maxVal;

            if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
                long[] decompressedData = decompressBlock(page, dataOffset, n);
                maxVal = decompressedData[n-1];
            }
            else {
                maxVal = maxValueInBlock(page, fc, n);
            }

            if (data.currentValue() > maxVal || retainInPage(page, flags, dataOffset, n, data)) {
                atEnd = (flags & FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.rejectAndAdvance();
                    return false;
                }

                // Consuming the block leaves the read pointer on the first value beyond
                // it, which is the value the forward pointers should be probed with
                long nextBlock;
                if (data.hasMore() && data.currentValue() > maxVal) {
                    nextBlock = findNextBlock(page, fc, data.currentValue());
                }
                else {
                    nextBlock = currentBlock + BLOCK_STRIDE;
                }

                if (nextBlock == currentBlock + BLOCK_STRIDE) {
                    sequentialReadsObserved++;
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

    boolean retainInPage(MemoryPage page, int flags, int dataOffset, int n, LongQueryBuffer data) {
        if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
            return retainInPage_Compressed(n, data);
        }
        else {
            return retainInPage_Plain(page, dataOffset, n, data);
        }
    }

    boolean retainInPage_Plain(MemoryPage page, int dataOffset, int n, LongQueryBuffer data) {

        while (data.hasMore()
                && n > (currentBlockIdx = page.binarySearchLong(data.currentValue(), dataOffset, currentBlockIdx, n)))
        {
            if (data.currentValue() != page.getLong( dataOffset + currentBlockIdx * 8)) {
                data.rejectAndAdvance();
            }
            else {
                data.retainAndAdvance();
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
                    currentBlockIdx++;
                    continue outer;
                }
            }
            break;
        }

        return currentBlockIdx >= n;
    }

    boolean retainInPage_Compressed(int n, LongQueryBuffer data) {
        long[] decompressedData = decompressedBlock.data;

        while (data.hasMore()
                && n > (currentBlockIdx = binarySearchUB(decompressedData, data.currentValue(), currentBlockIdx, n)))
        {
            if (data.currentValue() != decompressedData[currentBlockIdx]) {
                data.rejectAndAdvance();
            }
            else {
                data.retainAndAdvance();
                break;
            }
        }

        outer:
        while (data.hasMore()) {
            long bv = data.currentValue();

            for (; currentBlockIdx < n; currentBlockIdx++) {
                long pv = decompressedData[currentBlockIdx];
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


    /** The retain operation keeps all keys in the provided LongQueryBuffer that also
     * exist in the skip list index.  This operation will return after intersecting with
     * a single page, and return true if additional computation is available.
     */
    public boolean tryRejectData(@NotNull LongQueryBuffer data) {
        assert data.isAscending();

        try (var page = indexPool.get(currentBlock, readAhead())) {

            int n = headerNumRecords(page, currentBlockOffset);
            int fc = headerForwardCount(page, currentBlockOffset);
            int flags = headerFlags(page, currentBlockOffset);

            int dataOffset = pageDataOffset(currentBlockOffset, fc);

            long maxVal;
            if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
                long[] decompressedData = decompressBlock(page, dataOffset, n);
                maxVal = decompressedData[n-1];
            }
            else {
                maxVal = maxValueInBlock(page, fc, n);
            }

            if (data.currentValue() > maxVal || rejectInPage(page, flags, dataOffset, n, data)) {
                atEnd = (flags & FLAG_END_BLOCK) != 0;
                if (atEnd) {
                    while (data.hasMore())
                        data.retainAndAdvance();
                    return false;
                }

                // Consuming the block leaves the read pointer on the first value beyond
                // it, which is the value the forward pointers should be probed with
                long nextBlock;
                if (data.hasMore() && data.currentValue() > maxVal) {
                    nextBlock = findNextBlock(page, fc, data.currentValue());
                }
                else {
                    nextBlock = currentBlock + BLOCK_STRIDE;
                }

                if (nextBlock == currentBlock + BLOCK_STRIDE) {
                    sequentialReadsObserved++;
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

    boolean rejectInPage(MemoryPage page, int flags, int dataOffset, int n, LongQueryBuffer data) {
        if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
            decompressBlock(page, dataOffset, n);
            return rejectInPage_Compressed(n, data);
        }
        else {
            return rejectInPage_Plain(page, dataOffset, n, data);
        }
    }

    boolean rejectInPage_Compressed(int n, LongQueryBuffer data) {
        long[] decompressedData = decompressedBlock.data;

        while (data.hasMore()
                && n > (currentBlockIdx = binarySearchUB(decompressedData, data.currentValue(), currentBlockIdx, n)))
        {
            if (data.currentValue() != decompressedData[currentBlockIdx]) {
                data.retainAndAdvance();
            }
            else {
                data.rejectAndAdvance();
                break;
            }
        }

        outer:
        while (data.hasMore()) {
            long bv = data.currentValue();

            for (; currentBlockIdx < n; currentBlockIdx++) {
                long pv = decompressedData[currentBlockIdx];
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

    boolean rejectInPage_Plain(MemoryPage page, int dataOffset, int n, LongQueryBuffer data) {

        while (data.hasMore()
                && n > (currentBlockIdx = page.binarySearchLong(data.currentValue(), dataOffset, currentBlockIdx, n)))
        {
            if (data.currentValue() != page.getLong( dataOffset + currentBlockIdx * 8)) {
                data.retainAndAdvance();
            }
            else {
                data.rejectAndAdvance();
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
            try (var page = indexPool.get(currentBlock, BLOCK_READ_AHEAD_MAX)) {
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

                if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
                    long[] decompressedData = decompressBlock(page, dataOffset, n);
                    int nCopied = dest.addData(decompressedData, currentBlockIdx, n - currentBlockIdx);
                    currentBlockIdx += nCopied;
                    totalCopied += nCopied;
                }
                else {
                    int nCopied = dest.addData(ms, dataOffset + currentBlockIdx * 8L, n - currentBlockIdx);
                    currentBlockIdx += nCopied;
                    totalCopied += nCopied;
                }

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
     * @return the number of items added to the buffer
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
                boolean inRange = false;
                int dataOffset = pageDataOffset(currentBlockOffset, fc);

                if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
                    long[] decompressedData = decompressBlock(page, dataOffset, n);

                    do {
                        long blockMinValue = decompressedData[currentBlockIdx];
                        long rangeEnd;
                        while ((rangeEnd = ranges.end()) < blockMinValue) {
                            if (!ranges.next()) {
                                atEnd = true;
                                break outer;
                            }
                        }

                        long rangeStart = ranges.start();

                        int dataStart = binarySearchUB(decompressedData, rangeStart, currentBlockIdx, n);

                        if (dataStart == n) {
                            break;
                        }

                        int dataEnd = binarySearchUB(decompressedData, rangeEnd, dataStart, n);
                        if (dataStart != dataEnd) {
                            int nCopied = dest.addData(decompressedData, dataStart, dataEnd - dataStart);

                            totalCopied += nCopied;
                            currentBlockIdx = dataStart + nCopied;

                            if (nCopied < dataEnd - dataStart) {
                                return totalCopied;
                            }

                            if (dataEnd == n) {
                                inRange = true;
                                break;
                            }
                        }
                    } while (ranges.next());
                }
                else {
                    do {
                        long blockMinValue = ms.get(ValueLayout.JAVA_LONG, dataOffset + 8L * currentBlockIdx);
                        long rangeEnd;
                        while ((rangeEnd = ranges.end()) < blockMinValue) {
                            if (!ranges.next()) {
                                atEnd = true;
                                break outer;
                            }
                        }

                        long rangeStart = ranges.start();

                        int dataStart = page.binarySearchLong(rangeStart, dataOffset, currentBlockIdx, n);

                        if (dataStart == n) {
                            break;
                        }

                        int dataEnd = page.binarySearchLong(rangeEnd, dataOffset, dataStart, n);
                        if (dataStart != dataEnd) {
                            int nCopied = dest.addData(ms, dataOffset + dataStart * 8L, dataEnd - dataStart);

                            totalCopied += nCopied;
                            currentBlockIdx = dataStart + nCopied;

                            if (nCopied < dataEnd - dataStart) {
                                return totalCopied;
                            }

                            if (dataEnd == n) {
                                inRange = true;
                                break;
                            }
                        }
                    } while (ranges.next());
                }
                atEnd = (flags & FLAG_END_BLOCK) != 0 || ranges.atEnd();

                if (atEnd)
                    break;

                long nextBlock;
                if (inRange) {
                    nextBlock = currentBlock + (long) BLOCK_STRIDE;
                }
                else {
                    nextBlock = findNextBlock(page, fc, ranges.start());
                }

                currentBlockOffset = 0;
                currentBlockIdx = 0;
                currentBlock = nextBlock;
            }
        }

        return totalCopied;
    }


    public class ValueReader {

        private final int entrySize = (SkipListConstants.RECORD_SIZE - 1);

        private final MemorySegment valueSegment;

        /** Set when value blocks are to be fetched a batch at a time */
        @Nullable
        private final ValueBatchContext batchContext;

        private final SegmentAllocator allocator;

        /** Destination for a batch of value blocks, allocated on the first batched
         *  read since most readers never make one */
        private MemorySegment batchSlab;
        private final long[] batchBlocks;
        private int batchCount;
        private int batchCursor;

        private final long[] inputKeys;
        private int iPos = -1;
        private int offsetPos = 0;

        private final long[] valueOffsets;
        private int vPos = 0;
        private int vLen = 0;

        private final long[] outValues;
        private int oPos = -entrySize;
        private int oLen = 0;

        ValueReader() {
            inputKeys = new long[0];
            valueOffsets = new long[0];
            outValues = new long[0];
            valueSegment = null;
            batchContext = null;
            allocator = null;
            batchBlocks = null;
        }

        ValueReader(SegmentAllocator allocator, long[] inputKeys, @Nullable ValueBatchContext batchContext) {
            this.inputKeys = inputKeys;
            this.valueOffsets = new long[inputKeys.length];
            this.outValues = new long[inputKeys.length * (RECORD_SIZE-1)];
            this.batchContext = batchContext;
            this.allocator = allocator;
            this.batchBlocks = batchContext == null ? null : new long[ValueBatchContext.BATCH_BLOCKS];
            valueSegment = allocator.allocate(VALUE_BLOCK_SIZE, 8);
        }

        public boolean advance() throws IOException {
            oPos += entrySize;
            iPos++;

            if (oPos < oLen) return true;

            oPos = oLen = 0;

            if (vPos == vLen) readOffsets();
            if (vPos != vLen) {
                copyValuesFromBlock();

                return oLen > 0;
            }
            else {
                return false;
            }
        }

        public long getValue(int idx) {
            assert idx >= 0;
            assert idx < entrySize;

            return outValues[oPos + idx];
        }

        public int getIndex() {
            return iPos;
        }

        /** The contents of a value block.  With a batch context the blocks the
         *  rest of this window needs are read together on the first miss, since
         *  their offsets are all known by then. */
        private MemorySegment fetchBlock(long valBlock) throws IOException {
            if (batchContext != null) {
                MemorySegment block = heldBlock(valBlock);
                if (block == null) {
                    readBatch();
                    block = heldBlock(valBlock);
                }
                if (block != null) {
                    return block;
                }
            }

            valuesReader.read(valueSegment, valBlock);
            return valueSegment;
        }

        /** The block if this reader's last batch holds it.  Blocks are consumed in
         *  the order they were read, so the search only moves forward. */
        private MemorySegment heldBlock(long valBlock) {
            for (int i = batchCursor; i < batchCount; i++) {
                if (batchBlocks[i] == valBlock) {
                    batchCursor = i;
                    return batchSlab.asSlice((long) VALUE_BLOCK_SIZE * i, VALUE_BLOCK_SIZE);
                }
            }
            return null;
        }

        /** Read the distinct blocks the rest of this offset window points at */
        private void readBatch() throws IOException {
            batchCount = 0;
            batchCursor = 0;

            long previous = Long.MIN_VALUE;
            for (int i = vPos; i < vLen && batchCount < batchBlocks.length; i++) {
                if (valueOffsets[i] < 0) {
                    continue;
                }

                long block = valueOffsets[i] & -(long) VALUE_BLOCK_SIZE;
                if (block != previous) {
                    batchBlocks[batchCount++] = block;
                    previous = block;
                }
            }

            if (batchCount == 0) {
                return;
            }

            if (batchSlab == null) {
                batchSlab = allocator.allocate((long) VALUE_BLOCK_SIZE * batchBlocks.length, 8);
            }

            if (batchCount == 1) {
                // A single block has nothing to overlap with, and a submission
                // costs more than the read it would carry
                valuesReader.read(batchSlab.asSlice(0, VALUE_BLOCK_SIZE), batchBlocks[0]);
            }
            else {
                batchContext.readBlocks(batchSlab, batchBlocks, batchCount, VALUE_BLOCK_SIZE);
            }
        }

        private void copyValuesFromBlock() throws IOException {
            while (vPos < vLen && oLen == 0) {
                if (valueOffsets[vPos] < 0) {
                    Arrays.fill(outValues, oLen, oLen + entrySize, 0);
                    oLen+=entrySize;
                    vPos++;
                }
                else {
                    long valBlock = valueOffsets[vPos] & -VALUE_BLOCK_SIZE;

                    MemorySegment block = fetchBlock(valBlock);

                    for (; vPos < vLen; vPos++) {
                        if (valueOffsets[vPos] < 0) {
                            Arrays.fill(outValues, oLen, oLen + entrySize, 0);
                            oLen+=entrySize;
                        }
                        else {
                            long nextBlock = valueOffsets[vPos] & -VALUE_BLOCK_SIZE;
                            if (nextBlock != valBlock) {
                                break;
                            }

                            int offsetBase = (int) (valueOffsets[vPos] & (VALUE_BLOCK_SIZE - 1));
                            for (int j = 0; j < RECORD_SIZE - 1; j++) {
                                outValues[oLen + j] = block.get(ValueLayout.JAVA_LONG, offsetBase + 8*j);
                            }
                            oLen+=entrySize;
                        }
                    }
                }
            }

        }

        private void readOffsets() {

            final int vLen0 = vLen;
            while (vLen == vLen0 && offsetPos < inputKeys.length && !atEnd) {
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
                        return;

                    if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
                        if (currentBlockIdx == 0) {
                            long packed = NativeAlgos.decompressMatch(page.getMemorySegment(), dataOffset, n,
                                    inputKeys, offsetPos, valuesOffset, 8L * (RECORD_SIZE - 1), valueOffsets, vLen);

                            currentBlockIdx = (int) (packed >>> 32);
                            int newOffsetPos = (int) packed;
                            vLen += newOffsetPos - offsetPos;
                            offsetPos = newOffsetPos;
                        }
                        else {
                            decompressBlock(page, dataOffset, n);
                            readOffsetsForBlock_Compressed(n, valuesOffset);
                        }
                    }
                    else {
                        readOffsetsForBlock_Plain(page, n, dataOffset, valuesOffset);
                    }

                    if (currentBlockIdx >= n) {
                        atEnd = (flags & FLAG_END_BLOCK) != 0;
                        if (atEnd) {
                            return;
                        }

                        if (offsetPos >= inputKeys.length) {
                            currentBlock += BLOCK_STRIDE;
                            currentBlockOffset = 0;
                            currentBlockIdx = 0;
                        } else {
                            long nextBlock = findNextBlock(page, fc, inputKeys[offsetPos]);

                            currentBlockOffset = 0;
                            currentBlockIdx = 0;
                            currentBlock = nextBlock;
                        }
                    }

                }
            }
        }

        private void readOffsetsForBlock_Compressed(int n, long valuesOffset) {
            long[] decompressedData = decompressedBlock.data;

            int searchStart = currentBlockIdx;

            while (offsetPos < inputKeys.length && currentBlockIdx < n) {
                long kv = inputKeys[offsetPos];

                if (decompressedData[currentBlockIdx] < kv) {
                    int lo = currentBlockIdx;
                    int step = 1;
                    while (lo + step < n && decompressedData[lo + step] < kv) {
                        lo += step;
                        step <<= 1;
                    }

                    currentBlockIdx = binarySearchUB(decompressedData, kv, lo, Math.min(n, lo + step));
                    if (currentBlockIdx >= n) {
                        break;
                    }
                }

                if (decompressedData[currentBlockIdx] == kv) {
                    valueOffsets[vLen++] = valuesOffset + 8L * (currentBlockIdx - searchStart) * (RECORD_SIZE - 1);
                }
                else {
                    valueOffsets[vLen++] = -1;
                }
                offsetPos++;
            }
        }

        private void readOffsetsForBlock_Plain(MemoryPage page, int n, int dataOffset, long valuesOffset) {
            int remainingToRead = n - currentBlockIdx;

            int searchStart = currentBlockIdx;

            outer:
            while (offsetPos < inputKeys.length) {
                long kv = inputKeys[offsetPos];

                for (; currentBlockIdx < searchStart + remainingToRead; currentBlockIdx++) {
                    long pv = page.getLong(dataOffset + currentBlockIdx * 8);
                    if (kv < pv) {
                        offsetPos++;
                        valueOffsets[vLen++] = -1;
                        continue outer;
                    } else if (kv == pv) {
                        long val = valuesOffset + 8L * (currentBlockIdx - searchStart) * (RECORD_SIZE - 1);
                        valueOffsets[vLen++] = val;
                        offsetPos++;

                        continue outer;
                    }
                }
                break;
            }
        }

    }

    public ValueReader getValueReader(SegmentAllocator segmentAllocator, long[] keys) {
        return new ValueReader(segmentAllocator, keys, null);
    }

    public ValueReader getValueReader(SegmentAllocator segmentAllocator, long[] keys, @Nullable ValueBatchContext batchContext) {
        return new ValueReader(segmentAllocator, keys, batchContext);
    }

    public ValueReader getEmptyValueReader() {
        return new ValueReader();
    }

    /** Gets all of the values associated with the keys provided as input.
     * Values that are not found in the skip list index are set to zero.
     *
     * To help with cache locality when utilizing the data, the values are
     * de-interleaved in the result array, so for a record size of 3,
     * the result array will look like [ 1, 2, 3, 4, ..., 1, 2, 3, 4, ... ]
     * */
    public long[] getAllValues(long[] keys) throws IOException {
        var reader = getValueReader(Arena.ofAuto(), keys);
        long[] vals = new long[keys.length * (RECORD_SIZE-1)];

        while (reader.advance()) {
            vals[reader.getIndex()] = reader.getValue(0);
            vals[keys.length + reader.getIndex()] = reader.getValue(1);
        }

        return vals;
    }

    public BitSet getAllPresentValues(long[] keys) {
        BitSet ret = new BitSet(keys.length);

        if (getClass().desiredAssertionStatus()) {
            for (int i = 1; i < keys.length; i++) {
                assert keys[i] >= keys[i-1] : "Not ascending: " + Arrays.toString(keys);
            }
        }

        for (int pos = 0; pos < keys.length; ) {
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

                if (FLAG_COMPRESSED_BLOCK == (flags & FLAG_COMPRESSED_BLOCK)) {
                    long[] decompressedData = decompressBlock(page, dataOffset, n);
                    outer:
                    while (pos < keys.length) {
                        long kv = keys[pos];

                        for (; currentBlockIdx < searchStart + remainingToRead; currentBlockIdx++) {
                            long pv = decompressedData[currentBlockIdx];
                            if (kv < pv) {
                                pos++;
                                continue outer;
                            } else if (kv == pv) {
                                ret.set(pos);
                                pos++;
                                continue outer;
                            }
                        }
                        break;
                    }
                }
                else {
                    outer:
                    while (pos < keys.length) {
                        long kv = keys[pos];

                        for (; currentBlockIdx < searchStart + remainingToRead; currentBlockIdx++) {
                            long pv = page.getLong(dataOffset + currentBlockIdx * 8);
                            if (kv < pv) {
                                pos++;
                                continue outer;
                            } else if (kv == pv) {
                                ret.set(pos);
                                pos++;
                                continue outer;
                            }
                        }
                        break;
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
                        long nextBlock = findNextBlock(page, fc, keys[pos]);

                        currentBlockOffset = 0;
                        currentBlockIdx = 0;
                        currentBlock = nextBlock;
                    }
                }
            }
        }

        return ret;
    }

    /** Return the last (and largest) value in this page */
    private long maxValueInBlock(MemoryPage page, int fc, int n) {
        return page.getLong(pageDataOffset(currentBlockOffset, fc) + 8*(n-1));
    }

    private int readAhead() {
        // Readahead if we've seen sequentail read behavior
        if (sequentialReadsObserved >= 2)
            return BLOCK_READ_AHEAD_MAX;
        return Math.min(1, BLOCK_READ_AHEAD_MIN);
    }

    private long findNextBlock(MemoryPage page, int fc, long targetValue) {
        // The pointer distances are not strictly increasing in the V0 format due to a construction bug.
        // TODO: After 2027-01-01 we can drop support for this historical quirk and simplify the function
        int furthestBelow = 0;

        for (int i = 0; i < fc; i++) {
            long blockMaxValue = page.getLong(currentBlockOffset + DATA_BLOCK_HEADER_SIZE + 8 * i);
            if (blockMaxValue >= targetValue) {
                return currentBlock + (long) BLOCK_STRIDE * (furthestBelow + 1);
            }
            furthestBelow = Math.max(furthestBelow, format.skipOffsetForPointer(i));
        }

        return currentBlock + (long) BLOCK_STRIDE * Math.max(1, furthestBelow);
    }


    /** Binary search function with the same semantics as
     * MemoryPage.binarySearchLong, which are not the same as
     * Arrays.binarySearch
     * */
    public int binarySearchUB(long[] data, long key, int fromIndex, int toIndex) {
        assert fromIndex <= toIndex;
        assert fromIndex >= 0;

        int low = 0;
        int len = toIndex - fromIndex;

        while (len > 0) {
            var half = len / 2;
            long val = data[fromIndex + low + half];
            if (val < key) {
                low += len - half;
            } else if (val == key) {
                low += half;
                break;
            }
            len = half;
        }

        return fromIndex + low;
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
        long recordEnd = BLOCK_SIZE - offset;

        // assert n <= MAX_RECORDS_PER_BLOCK : "Invalid header, n = " + n;
        assert (flags & FLAG_VALUE_BLOCK) == 0 : "Attempting to parse value block";

        offset += DATA_BLOCK_HEADER_SIZE;

        LongList forwardPointers = new LongArrayList(fc);
        for (int i = 0; i < fc; i++) {
            forwardPointers.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L*i));
        }
        offset += 8L*fc;

        LongList docIds = new LongArrayList(n);

        if ((flags & FLAG_COMPRESSED_BLOCK) == FLAG_COMPRESSED_BLOCK) {
            DocIdCompressor.decompress(new SegmentCompressorBuffer(seg, offset), n, docIds);
        }
        else {
            long currentBlock = offset & - BLOCK_SIZE;
            long lastDataBlock = (offset + 8L * (n-1)) & - BLOCK_SIZE;

            if (currentBlock != lastDataBlock) {
                throw new IllegalStateException("Last data block is not the same as the current data block (n=" + n +", flags=" + flags + ")" + " for block offset " + (offset & (BLOCK_SIZE - 1)));
            }

            for (int i = 0; i < n; i++) {
                docIds.add(seg.get(ValueLayout.JAVA_LONG, offset + 8L * i));
            }
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
