package nu.marginalia.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Hands out pooled {@link ScratchSegmentAllocator}s.
 * <p>
 * Slabs are allocated from the global arena and live for the lifetime of the process.
 * The pool grows to the peak number of concurrently borrowed allocators, which is
 * bounded by the number of simultaneous query executions and their stage threads.
 * <p>
 * When the system property system.scratchAllocatorAutoArena is set, slabs are backed
 * by automatic arenas instead, so that tests do not accumulate permanent allocations.
 */
public class ScratchSegmentAllocatorFactory
{
    private static final Logger logger = LoggerFactory.getLogger(ScratchSegmentAllocatorFactory.class);
    private static final boolean useAutoArena = Boolean.getBoolean("system.scratchAllocatorAutoArena");

    private final String name;
    private final int slabSize;
    private final ConcurrentLinkedQueue<ScratchSegmentAllocator> pool = new ConcurrentLinkedQueue<>();

    public ScratchSegmentAllocatorFactory(String name, int slabSize) {
        this.name = name;
        this.slabSize = slabSize;
    }

    public ScratchSegmentAllocator createAllocator() {
        ScratchSegmentAllocator allocator = pool.poll();
        if (allocator != null) {
            return allocator;
        }

        logger.debug("Allocating a new {} scratch slab of {} bytes", name, slabSize);

        Arena arena = useAutoArena ? Arena.ofAuto() : Arena.global();
        return new ScratchSegmentAllocator(this, arena.allocate(slabSize, ScratchSegmentAllocator.SLAB_ALIGNMENT));
    }

    public void release(ScratchSegmentAllocator allocator) {
        pool.add(allocator);
    }
}
