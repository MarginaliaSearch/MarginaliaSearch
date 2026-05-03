package nu.marginalia.domaingraph;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class EdgeBloomFilter implements AutoCloseable {
    // Use off-heap memory as it's very likely we'll at some point
    // come to want to exceed the 2GB limit for on-heap arrays.
    private final Arena arena;
    private final MemorySegment filter;

    private final int filterSizeLongs;

    EdgeBloomFilter(int nVertices, int filterSizeLongs) {
        this.filterSizeLongs = filterSizeLongs;
        this.arena = Arena.ofShared();

        filter = arena.allocate(JAVA_LONG, (long) filterSizeLongs * nVertices);
    }

    void populateRange(int idx, int[] values, int start, int end) {
        for (int i = start; i < end; i++) {
            final int hash = hashInt(values[i]);

            long offset = (long) idx * filterSizeLongs + Integer.remainderUnsigned(hash, filterSizeLongs);

            long val = filter.getAtIndex(JAVA_LONG, offset);
            val |= Long.rotateLeft(1, hash / filterSizeLongs);
            filter.setAtIndex(JAVA_LONG, offset, val);
        }
    }

    // MurmurHash3 32-bit finalizer
    private int hashInt(int value) {
        int hash = value;
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        hash *= 0xc2b2ae35;
        hash ^= hash >>> 16;
        return hash;
    }

    /** Check if two edge sets may overlap */
    public boolean mayOverlap(int aIdx, int bIdx) {
        long aOffset = (long) aIdx * filterSizeLongs;
        long bOffset = (long) bIdx * filterSizeLongs;

        for (int i = 0; i < filterSizeLongs; i++) {
            long aHash = filter.getAtIndex(JAVA_LONG, aOffset + i);
            long bHash = filter.getAtIndex(JAVA_LONG, bOffset + i);

            if ((aHash & bHash) != 0)  {
                return true;
            }
        }

        return false;
    }

    @Override
    public void close() {
        arena.close();
    }
}
