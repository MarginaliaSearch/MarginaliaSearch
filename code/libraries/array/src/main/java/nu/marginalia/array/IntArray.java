package nu.marginalia.array;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.array.algo.IntArrayBase;
import nu.marginalia.array.algo.IntArraySearch;
import nu.marginalia.array.algo.IntArraySort;
import nu.marginalia.array.algo.IntArrayTransformations;
import nu.marginalia.array.delegate.ShiftedIntArray;
import nu.marginalia.array.page.IntArrayPage;
import nu.marginalia.array.page.PagingIntArray;
import nu.marginalia.array.scheme.ArrayPartitioningScheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface IntArray extends IntArrayBase, IntArrayTransformations, IntArraySearch, IntArraySort {
    int WORD_SIZE = 4;

    ArrayPartitioningScheme DEFAULT_PARTITIONING_SCHEME
            = ArrayPartitioningScheme.forPartitionSize(Integer.getInteger("wmsa.page-size",1<<30) / WORD_SIZE);

    int MAX_CONTINUOUS_SIZE = Integer.MAX_VALUE/WORD_SIZE - 16;

    static IntArray allocate(long size) {
        if (size < MAX_CONTINUOUS_SIZE) {
            return IntArrayPage.onHeap((int) size);
        }

        return PagingIntArray.newOnHeap(DEFAULT_PARTITIONING_SCHEME, size);
    }

    static IntArray mmapRead(Path path) throws IOException {
        long sizeBytes = Files.size(path);

        if (sizeBytes < MAX_CONTINUOUS_SIZE) {
            return IntArrayPage.fromMmapReadOnly(path, 0, (int) sizeBytes / 4);
        }

        return PagingIntArray.mapFileReadOnly(DEFAULT_PARTITIONING_SCHEME, path);
    }

    static IntArray mmapForWriting(Path path) throws IOException {
        return PagingIntArray.mapFileReadWrite(DEFAULT_PARTITIONING_SCHEME, path);
    }

    static IntArray mmapForWriting(Path path, long size) throws IOException {
        return PagingIntArray.mapFileReadWrite(DEFAULT_PARTITIONING_SCHEME, path, size);
    }

    default ShiftedIntArray shifted(long offset) {
        return new ShiftedIntArray(offset, this);
    }
    default ShiftedIntArray range(long start, long end) {
        return new ShiftedIntArray(start, end, this);
    }

    /** Translate the range into the equivalent range in the underlying array if they are in the same page */
    ArrayRangeReference<IntArray> directRangeIfPossible(long start, long end);

    void force();


    void advice(NativeIO.Advice advice) throws IOException;
    void advice(NativeIO.Advice advice, long start, long end) throws IOException;

}
