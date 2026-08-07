package nu.marginalia.index;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/** Bump allocator for short-lived scratch memory used during query execution.
 * <p>
 * Instances are pooled by their factory and keep their slab across leases, so that
 * steady state query execution performs no native memory allocation.  Requests that
 * do not fit in the slab are served from a temporary overflow arena that lives until
 * the next reset.
 * <p>
 * The returned segments are uninitialized, and slab backed segments remain technically
 * accessible after reset, so callers must treat them strictly as scratch space.
 */
public class ScratchSegmentAllocator implements SegmentAllocator, AutoCloseable {
    /** Slabs are page aligned, so aligning an offset within the slab aligns the
     *  address it resolves to, and any request up to this can be served from the
     *  slab rather than falling out to the overflow arena */
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
