package nu.marginalia.skiplist;

import nu.marginalia.ffi.IoUring;
import nu.marginalia.uring.UringQueue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** Submission channel for reading several value blocks in one io_uring batch
 *  instead of one blocking pread per block, letting their page cache misses
 *  overlap in the device queue rather than serialize.
 *  <p>
 *  A context owns a ring on the value file's descriptor, may only be used by
 *  one thread at a time, and must be discarded when the index it was opened
 *  against is swapped out, which owner() helps detect.
 */
public class ValueBatchContext implements AutoCloseable {

    /** Blocks per submission.  Each reader's destination buffer is this many
     *  blocks, so it is kept small.  Deeper batches are read in several waves. */
    public static final int BATCH_BLOCKS = 16;

    private static final int RING_SIZE = 128;

    private final SkipListValueReader owner;
    private final UringQueue ring;

    private final Arena arena;
    private final MemorySegment buffers;
    private final MemorySegment sizes;
    private final MemorySegment offsets;

    ValueBatchContext(SkipListValueReader owner, int fd) {
        this.owner = owner;
        this.ring = UringQueue.open(fd, RING_SIZE);

        arena = Arena.ofShared();
        try {
            buffers = arena.allocate(8L * BATCH_BLOCKS, 8);
            sizes = arena.allocate(4L * BATCH_BLOCKS, 8);
            offsets = arena.allocate(8L * BATCH_BLOCKS, 8);
        }
        catch (RuntimeException e) {
            ring.close();
            arena.close();
            throw e;
        }
    }

    public SkipListValueReader owner() {
        return owner;
    }

    /** Read blocks[0:n] into consecutive slots of dest, in one submission */
    void readBlocks(MemorySegment dest, long[] blocks, int n, int blockSize) {
        for (int i = 0; i < n; i++) {
            buffers.setAtIndex(JAVA_LONG, i, dest.address() + (long) blockSize * i);
            sizes.setAtIndex(JAVA_INT, i, blockSize);
            offsets.setAtIndex(JAVA_LONG, i, blocks[i]);
        }

        int ret = IoUring.readBatchRaw(ring, n, buffers.address(), sizes.address(), offsets.address());
        if (ret != n) {
            throw new IllegalStateException("Batch read failed: " + ret + " of " + n);
        }
    }

    @Override
    public void close() {
        ring.close();
        arena.close();
    }
}
