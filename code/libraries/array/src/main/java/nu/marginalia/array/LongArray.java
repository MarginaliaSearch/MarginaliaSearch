package nu.marginalia.array;

import com.upserve.uppend.blobs.NativeIO;
import nu.marginalia.array.algo.LongArrayBase;
import nu.marginalia.array.algo.LongArraySearch;
import nu.marginalia.array.algo.LongArraySort;
import nu.marginalia.array.algo.LongArrayTransformations;
import nu.marginalia.array.delegate.ShiftedLongArray;
import nu.marginalia.array.page.LongArrayPage;
import nu.marginalia.array.page.PagingLongArray;
import nu.marginalia.array.scheme.ArrayPartitioningScheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public interface LongArray extends LongArrayBase, LongArrayTransformations, LongArraySearch, LongArraySort {
    int WORD_SIZE = 8;

    ArrayPartitioningScheme DEFAULT_PARTITIONING_SCHEME
            = ArrayPartitioningScheme.forPartitionSize(Integer.getInteger("wmsa.page-size",1<<30) / WORD_SIZE);

    int MAX_CONTINUOUS_SIZE = Integer.MAX_VALUE/WORD_SIZE - 8;

    static LongArray allocate(long size) {
        if (size < MAX_CONTINUOUS_SIZE) {
            return LongArrayPage.onHeap((int) size);
        }

        return PagingLongArray.newOnHeap(DEFAULT_PARTITIONING_SCHEME, size);
    }

    static LongArray mmapRead(Path path) throws IOException {
        long sizeBytes = Files.size(path);
        
        if (sizeBytes < MAX_CONTINUOUS_SIZE) {
            return LongArrayPage.fromMmapReadOnly(path, 0, (int) sizeBytes / 8);
        }

        return PagingLongArray.mapFileReadOnly(DEFAULT_PARTITIONING_SCHEME, path);
    }

    /** Map an existing file for writing */
    static LongArray mmapForModifying(Path path) throws IOException {
        return PagingLongArray.mapFileReadWrite(DEFAULT_PARTITIONING_SCHEME, path);
    }

    static LongArray mmapForWriting(Path path, long size) throws IOException {
        return PagingLongArray.mapFileReadWrite(DEFAULT_PARTITIONING_SCHEME, path, size);
    }

    default ShiftedLongArray shifted(long offset) {
        return new ShiftedLongArray(offset, this);
    }
    default ShiftedLongArray range(long start, long end) {
        return new ShiftedLongArray(start, end, this);
    }

    /** Translate the range into the equivalent range in the underlying array if they are in the same page */
    ArrayRangeReference<LongArray> directRangeIfPossible(long start, long end);

    void force();

    void advice(NativeIO.Advice advice) throws IOException;
    void advice(NativeIO.Advice advice, long start, long end) throws IOException;
}
