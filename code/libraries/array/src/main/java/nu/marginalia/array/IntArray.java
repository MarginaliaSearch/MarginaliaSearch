package nu.marginalia.array;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.array.algo.IntArrayBase;
import nu.marginalia.array.algo.IntArraySearch;
import nu.marginalia.array.algo.IntArraySort;
import nu.marginalia.array.algo.IntArrayTransformations;
import nu.marginalia.array.delegate.ShiftedIntArray;
import nu.marginalia.array.delegate.ShiftedLongArray;
import nu.marginalia.array.page.SegmentIntArray;
import nu.marginalia.array.page.SegmentLongArray;
import nu.marginalia.array.scheme.ArrayPartitioningScheme;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;

public interface IntArray extends IntArrayBase, IntArrayTransformations, IntArraySearch, IntArraySort {
    int WORD_SIZE = 4;

    ArrayPartitioningScheme DEFAULT_PARTITIONING_SCHEME
            = ArrayPartitioningScheme.forPartitionSize(Integer.getInteger("wmsa.page-size",1<<30) / WORD_SIZE);

    int MAX_CONTINUOUS_SIZE = Integer.MAX_VALUE/WORD_SIZE - 16;

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


    void advice(NativeIO.Advice advice) throws IOException;
    void advice(NativeIO.Advice advice, long start, long end) throws IOException;

    default void close() { }
}
