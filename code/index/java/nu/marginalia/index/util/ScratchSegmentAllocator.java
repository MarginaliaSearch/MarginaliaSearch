package nu.marginalia.index.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/** Bump allocator for short-lived scratch memory used during query execution.
 * <p>
 * Instances are pooled by their factory and keep their slab across leases,
 * so that query execution performs no native memory allocation unless they overflow the slab.
 */
public class ScratchSegmentAllocator implements SegmentAllocator, AutoCloseable {
    public static final long SLAB_ALIGNMENT = 4096;

    private final ScratchSegmentAllocatorFactory factory;
    private final MemorySegment slab;
    private long position = 0;

    private Arena overflowArena = null;

    ScratchSegmentAllocator(ScratchSegmentAllocatorFactory factory, MemorySegment slab) {
        this.factory = factory;
        this.slab = slab;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        // Aligning the offset only carries to the address as far as the slab
        // base is itself aligned
        if (byteAlignment > SLAB_ALIGNMENT) {
            return allocateOverflow(byteSize, byteAlignment);
        }

        long alignedPosition = (position + byteAlignment - 1) & -byteAlignment;
        if (alignedPosition + byteSize > slab.byteSize()) {
            return allocateOverflow(byteSize, byteAlignment);
        }

        position = alignedPosition + byteSize;
        return slab.asSlice(alignedPosition, byteSize);
    }

    private MemorySegment allocateOverflow(long byteSize, long byteAlignment) {
        if (overflowArena == null) {
            overflowArena = Arena.ofConfined();
        }
        return overflowArena.allocate(byteSize, byteAlignment);
    }

    public void reset() {
        position = 0;

        if (overflowArena != null) {
            overflowArena.close();
            overflowArena = null;
        }
    }

    public long slabSize() {
        return slab.byteSize();
    }

    /** Return the allocator to its factory's pool */
    @Override
    public void close() {
        reset();
        factory.release(this);
    }
}
