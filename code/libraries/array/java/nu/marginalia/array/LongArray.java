package nu.marginalia.array;

import nu.marginalia.array.algo.LongArrayBase;
import nu.marginalia.array.algo.LongArraySearch;
import nu.marginalia.array.algo.LongArraySort;
import nu.marginalia.array.algo.LongArrayTransformations;
import nu.marginalia.array.page.UnsafeLongArray;

import java.lang.foreign.Arena;


public interface LongArray extends LongArrayBase, LongArrayTransformations, LongArraySearch, LongArraySort, AutoCloseable {
    int WORD_SIZE = 8;
    int foo  = 3;

    @Deprecated
    static LongArray allocate(long size) {
        return UnsafeLongArray.onHeap(Arena.ofShared(), size);
    }

    LongArray shifted(long offset);
    LongArray range(long start, long end);

    /** Force any changes to be written to the backing store */
    void force();

    /** Close the array and release any resources */
    void close();
}
