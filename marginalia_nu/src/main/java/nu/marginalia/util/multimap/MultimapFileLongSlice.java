package nu.marginalia.util.multimap;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;

public interface MultimapFileLongSlice {
    long size();

    void put(long idx, long val);

    void setRange(long idx, int n, long val);

    long get(long idx);

    void read(long[] vals, long idx);

    void read(long[] vals, int n, long idx);

    void read(LongBuffer vals, long idx);

    void write(long[] vals, long idx);

    void write(long[] vals, int n, long idx);

    void write(LongBuffer vals, long idx);

    void write(LongBuffer vals, int n, long idx);

    void swapn(int n, long idx1, long idx2);

    void transferFromFileChannel(FileChannel sourceChannel, long destOffset, long sourceStart, long sourceEnd) throws IOException;

    default MultimapFileLongSlice atOffset(long off) {
        return new MultimapFileLongOffsetSlice(this, off);
    }
    long binarySearchInternal(long key, long fromIndex, int step, long n, long mask);
    long binarySearchInternal(long key, long fromIndex, long n, long mask);

    long binarySearchInternal(long key, long fromIndex, long n);

    long binarySearchUpperInternal(long key, long fromIndex, long n);

    long quickSortPartition(int wordSize, long low, long highInclusive);

    void insertionSort(int wordSize, long start, int n);
}
