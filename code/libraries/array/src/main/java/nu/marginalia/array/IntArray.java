package nu.marginalia.array;

import nu.marginalia.array.algo.IntArrayBase;
import nu.marginalia.array.algo.IntArraySearch;
import nu.marginalia.array.algo.IntArraySort;
import nu.marginalia.array.algo.IntArrayTransformations;
import nu.marginalia.array.delegate.ShiftedIntArray;
import nu.marginalia.array.page.SegmentIntArray;

import java.io.IOException;
import java.lang.foreign.Arena;

public interface IntArray extends IntArrayBase, IntArrayTransformations, IntArraySearch, IntArraySort {
    int WORD_SIZE = 4;

    static IntArray allocate(long size) {
        return SegmentIntArray.onHeap(Arena.ofShared(), size);
    }

    default IntArray shifted(long offset) {
        return new ShiftedIntArray(offset, this);
    }
    default IntArray range(long start, long end) {
        return new ShiftedIntArray(start, end, this);
    }

    /** Translate the range into the equivalent range in the underlying array if they are in the same page */
    ArrayRangeReference<IntArray> directRangeIfPossible(long start, long end);

    void force();

    default void close() { }
}
