package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;
import nu.marginalia.ffi.NativeAlgos;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public interface LongArraySort extends LongArrayBase {

    default boolean isSorted(long start, long end) {
        if (start == end) return true;

        long val = get(start);
        for (long i = start + 1; i < end; i++) {
            long next = get(i);
            if (next < val)
                return false;
            val = next;
        }

        return true;
    }

    /** For the given range of sorted values, retain only the first occurrence of each value. */
    default long keepUnique(long start, long end) {
        if (start == end)
            return start;

        assert isSorted(start, end);

        long val = get(start);
        long pos = start + 1;
        for (long i = start + 1; i < end; i++) {
            long next = get(i);
            if (next != val) {
                set(pos, next);
                pos++;
            }
            val = next;
        }

        return pos;
    }

    /** For the given range of sorted values, retain only the first occurrence of each value. */
    default long keepUniqueN(int sz, long start, long end) {
        if (start == end)
            return start;

        assert isSortedN(sz, start, end);
        assert (end - start) % sz == 0;

        long val = get(start);
        long pos = start + sz;
        for (long i = start + sz; i < end; i+=sz) {
            long next = get(i);
            if (next != val) {
                set(pos, next);
                for (int j = 1; j < sz; j++) {
                    set(pos + j, get(i + j));
                }
                pos+=sz;
            }
            val = next;
        }

        return pos;
    }

    default boolean isSortedN(int wordSize, long start, long end) {
        if (start == end) return true;

        long val = get(start);
        for (long i = start + wordSize; i < end; i+=wordSize) {
            long next = get(i);
            if (next < val)
                return false;
            val = next;
        }

        return true;
    }

    static void insertionSort(LongArraySort array, long start, long end) {
        SortAlgoInsertionSort._insertionSort(array, start, end);
    }

    static void insertionSortN(LongArraySort array, int sz, long start, long end) {
        if (sz == 2) {
            SortAlgoInsertionSort._insertionSort2(array, start, end);
        }
        else {
            SortAlgoInsertionSort._insertionSortN(array, sz, start, end);
        }
    }

    default void sort(long start, long end) {
        if (NativeAlgos.isAvailable) {
            NativeAlgos.sort(getMemorySegment(), start, end);
        } else {
            SortAlgoQuickSort._quickSortLH(this, start, end - 1);
        }
    }

    static void quickSortJava(LongArray array, long start, long end) {
        SortAlgoQuickSort._quickSortLH(array, start, end - 1);
    }

    default void quickSortN(int wordSize, long start, long end) {
        assert ((end - start) % wordSize) == 0;

        if (end == start)
            return;

        if (wordSize == 2) {
            if (NativeAlgos.isAvailable) {
                NativeAlgos.sort128(getMemorySegment(), start, end);
            }
            else {
                SortAlgoQuickSort._quickSortLH2(this, start, end - 2);
            }
        }
        else {
            SortAlgoQuickSort._quickSortLHN(this, wordSize, start, end - wordSize);
        }

        assert isSortedN(wordSize, start, end);
    }

    static void quickSortJavaN(LongArray array, int wordSize, long start, long end) {
        assert ((end - start) % wordSize) == 0;

        if (end == start)
            return;

        SortAlgoQuickSort._quickSortLHN(array, wordSize, start, end - wordSize);
    }

    static void quickSortJava2(LongArray array, long start, long end) {
        assert ((end - start) % 2) == 0;

        if (end == start)
            return;

        SortAlgoQuickSort._quickSortLH2(array, start, end - 2);
    }

    /** Don't use this method, it's slow. */
    @Deprecated
    default void mergeSortN(int wordSize, long start, long end, Path tmpDir) throws IOException {
        int length = (int) (end - start);
        assert (length % wordSize) == 0;

        Path tmpFile = Files.createTempFile(tmpDir,"sort-"+start+"-"+(start+length), ".dat");
        try (var channel = (FileChannel) Files.newByteChannel(tmpFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            var workBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 8L * length).asLongBuffer();

            SortAlgoMergeSort._mergeSortN(this, wordSize, start, length, workBuffer);
        }
        finally {
            Files.delete(tmpFile);
        }
    }


    /** Don't use this method, it's slow. */
    @Deprecated
    default void mergeSort(long start, long end, Path tmpDir) throws IOException {
        int length = (int) (end - start);

        Path tmpFile = Files.createTempFile(tmpDir,"sort-"+start+"-"+(start+length), ".dat");
        try (var channel = (FileChannel) Files.newByteChannel(tmpFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            var workBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 8L * length).asLongBuffer();

            SortAlgoMergeSort._mergeSort(this, start, length, workBuffer);
        }
        finally {
            Files.delete(tmpFile);
        }

    }

}
