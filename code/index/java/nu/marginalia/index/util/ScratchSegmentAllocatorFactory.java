package nu.marginalia.index.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Hands out pooled {@link ScratchSegmentAllocator}s.
 * <p>
 * Slabs are allocated from the global arena and live for the lifetime of the process.
 * The pool grows to the peak number of concurrently borrowed allocators, which is
 * bounded by the number of simultaneous query executions and their stage threads.
 */
public class ScratchSegmentAllocatorFactory
{
    private static final Arena arena;
    static {
        // Disable global arena for testing
        if (Boolean.getBoolean("system.scratchAllocatorAutoArena"))
            arena = Arena.ofAuto();
        else
            arena = Arena.global();
    }

    private final int slabSize;
    private final ConcurrentLinkedQueue<ScratchSegmentAllocator> pool = new ConcurrentLinkedQueue<>();

    public ScratchSegmentAllocatorFactory(int slabSize) {
        this.slabSize = slabSize;
    }

    public ScratchSegmentAllocator createAllocator() {
        ScratchSegmentAllocator allocator = pool.poll();
        if (allocator != null) {
            return allocator;
        }

        return new ScratchSegmentAllocator(this, arena.allocate(slabSize, ScratchSegmentAllocator.SLAB_ALIGNMENT));
    }

    public void release(ScratchSegmentAllocator allocator) {
        pool.add(allocator);
    }
}
