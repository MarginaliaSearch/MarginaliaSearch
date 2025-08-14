package nu.marginalia.uring;

import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import nu.marginalia.ffi.LinuxSystemCalls;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class UringFileReader implements AutoCloseable {
    private final UringQueue[] rings = new UringQueue[8];
    private final AtomicLong ringIdx = new AtomicLong();
    private final int fd;
    private final boolean direct;

    private static final int QUEUE_SIZE = 2048;

    public UringFileReader(Path filename, boolean direct) throws IOException {
        if (direct) {
            fd = LinuxSystemCalls.openDirect(filename);
            this.direct = true;
        }
        else {
            fd = LinuxSystemCalls.openBuffered(filename);
            LinuxSystemCalls.fadviseRandom(fd);
            this.direct = false;
        }
        for (int i = 0; i < rings.length; i++) {
            rings[i] = UringQueue.open(fd, QUEUE_SIZE);
        }
        if (fd < 0) {
            throw new IOException("Error opening direct file: " + filename);
        }
    }

    public void fadviseWillneed() {
        LinuxSystemCalls.fadviseWillneed(fd);
    }

    public void read(List<MemorySegment> destinations, List<Long> offsets) {
        if (destinations.size() < 5) {
            for (int  i = 0; i < destinations.size(); i++) {
                var ms = destinations.get(i);
                long offset = offsets.get(i);

                int ret;
                if (ms.byteSize() != (ret = LinuxSystemCalls.readAt(fd, ms, offset))) {
                    throw new RuntimeException("Read failed, rv=" + ret + " at " + offset + " : " + ms.byteSize());
                }
            }
            return;
        }
        var ring = rings[(int) (ringIdx.getAndIncrement() % rings.length)];

        if (destinations.size() <= QUEUE_SIZE) {
            int ret = ring.readBatch(destinations, offsets, direct);
            if (ret != offsets.size()) {
                throw new RuntimeException("Read failed, rv=" + ret);
            }
        }
        else {
            for (int i = 0; i < destinations.size(); i+=QUEUE_SIZE) {
                var destSlice = destinations.subList(i, Math.min(destinations.size(), i+QUEUE_SIZE));
                var offSlice = offsets.subList(i, Math.min(offsets.size(), i+QUEUE_SIZE));
                int ret = ring.readBatch(destSlice, offSlice, direct);
                if (ret != offSlice.size()) {
                    throw new RuntimeException("Read failed, rv=" + ret);
                }
            }
        }
    }

    public void read(List<MemorySegment> destinations, List<Long> offsets, long timeoutMs) throws TimeoutException {
        if (destinations.size() < 5) {
            for (int  i = 0; i < destinations.size(); i++) {
                var ms = destinations.get(i);
                long offset = offsets.get(i);

                int ret;
                if (ms.byteSize() != (ret = LinuxSystemCalls.readAt(fd, ms, offset))) {
                    throw new RuntimeException("Read failed, rv=" + ret + " at " + offset + " : " + ms.byteSize());
                }
            }
            return;
        }
        var ring = rings[(int) (ringIdx.getAndIncrement() % rings.length)];

        if (destinations.size() <= QUEUE_SIZE) {
            int ret = ring.readBatch(destinations, offsets, timeoutMs, direct);
            if (ret != offsets.size()) {
                throw new RuntimeException("Read failed, rv=" + ret);
            }
        }
        else {
            long timeEnd = System.currentTimeMillis() + timeoutMs;
            for (int i = 0; i < destinations.size(); i+=QUEUE_SIZE) {
                long timeRemainingMs = timeEnd - System.currentTimeMillis();
                if (timeRemainingMs <= 0)
                    throw new TimeoutException();

                var destSlice = destinations.subList(i, Math.min(destinations.size(), i+QUEUE_SIZE));
                var offSlice = offsets.subList(i, Math.min(offsets.size(), i+QUEUE_SIZE));
                int ret = ring.readBatch(destSlice, offSlice, timeRemainingMs, direct);
                if (ret != offSlice.size()) {
                    throw new RuntimeException("Read failed, rv=" + ret);
                }
            }
        }
    }


    public List<MemorySegment> readUnaligned(Arena arena, long timeoutMs, long[] offsets, int[] sizes, int blockSize) throws TimeoutException {
        if (direct) {
            return readUnalignedInDirectMode(arena, timeoutMs, offsets, sizes, blockSize);
        } else {
            return readUnalignedInBufferedMode(arena, timeoutMs, offsets, sizes);
        }
    }

    private List<MemorySegment> readUnalignedInBufferedMode(Arena arena, long timeoutMs, long[] offsets, int[] sizes) throws TimeoutException {
        int totalSize = 0;
        for (int size : sizes) {
            totalSize += size;
        }

        var allocator = SegmentAllocator.slicingAllocator(arena.allocate(totalSize));

        List<MemorySegment> segmentsList = new ArrayList<>(sizes.length);
        List<Long> offsetsList = new ArrayList<>(sizes.length);

        for (int i = 0; i < sizes.length; i++) {
            segmentsList.add(allocator.allocate(sizes[i]));
            offsetsList.add(offsets[i]);
        }

        read(segmentsList, offsetsList, timeoutMs);

        return segmentsList;
    }

    /** This function takes a list of offsets and sizes, and translates them to a minium of blockSize'd O_DIRECT
     * reads.  A single buffer will be allocated to hold all the data, to encourage HugePages allocation and
     * reduce TLB thrashing.  It is still generally helpful for performance if the data is at least best-effort
     * block aligned.
     *
     * @return MemorySegment slices that contain only the requested data.
     */
    public List<MemorySegment> readUnalignedInDirectMode(Arena arena, long timeoutMs, long[] offsets, int[] sizes, int blockSize) throws TimeoutException {

        if (offsets.length < 1)
            return List.of();
        if (offsets.length != sizes.length) throw new IllegalArgumentException("Offsets and Sizes arrays don't match!");
        if ((blockSize & 511) != 0) throw new IllegalArgumentException("Block size must be a multiple of 512");

        // First we work out which blocks we need to read, and how many they are
        final LongAVLTreeSet neededBlocks = new LongAVLTreeSet();

        for (int i = 0; i < offsets.length; i++) {
            for (long block = offsets[i] & -blockSize;
                 block <= ((offsets[i] + sizes[i]) & -blockSize);
                 block+=blockSize)
            {
                neededBlocks.add(block);
            }
        }

        MemorySegment allMemory = arena.allocate((long) blockSize * neededBlocks.size(), blockSize);

        List<MemorySegment> buffers = new ArrayList<>(sizes.length);
        List<Long> bufferOffsets = new ArrayList<>(sizes.length);

        final Long2IntAVLTreeMap blockToIdx  = new Long2IntAVLTreeMap();
        LongIterator neededBlockIterator = neededBlocks.longIterator();

        long runStart = -1;
        long runCurrent = -1;
        long sliceOffset = 0;

        for (;;) {
            long nextBlock = neededBlockIterator.nextLong();

            blockToIdx.put(nextBlock, blockToIdx.size());

            if (runStart < 0) runStart = nextBlock;
            else if (runCurrent + blockSize != nextBlock) {
                int bufferSize = (int) (blockSize + runCurrent - runStart);
                bufferOffsets.add(runStart);
                buffers.add(allMemory.asSlice(sliceOffset, bufferSize));
                sliceOffset += bufferSize;

                runStart = nextBlock;
            }

            runCurrent = nextBlock;

            if (!neededBlockIterator.hasNext()) {
                // If this is the last value, we need to wrap up the final run
                int bufferSize = (int) (blockSize + runCurrent - runStart);
                bufferOffsets.add(runStart);
                buffers.add(allMemory.asSlice(sliceOffset, bufferSize));
                break;
            }
        }

        // Perform the read
        read(buffers, bufferOffsets, timeoutMs);

        // Slice the big memory chunk into the requested slices
        List<MemorySegment> ret = new ArrayList<>(sizes.length);
        for (int i = 0; i < offsets.length; i++) {
            long offset = offsets[i];
            int size = sizes[i];

            long startBlock = (long) blockSize * blockToIdx.get(offset & -blockSize);
            long blockOffset = offset & (blockSize - 1);
            ret.add(allMemory.asSlice(startBlock + blockOffset, size));
        }

        return ret;
    }

    public void close() {
        for (var ring : rings) {
            ring.close();
        }
        LinuxSystemCalls.closeFd(fd);
    }
}
