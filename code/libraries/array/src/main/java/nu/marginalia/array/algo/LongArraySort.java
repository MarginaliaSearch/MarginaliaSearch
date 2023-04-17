package nu.marginalia.array.algo;

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

    default void sortLargeSpan(SortingContext ctx, long start, long end) throws IOException {
        long size = end - start;

        if (size < ctx.memorySortLimit()) {
            quickSort(start, end);
        }
        else {
            mergeSort(start, end, ctx.tempDir());
        }
    }

    default void sortLargeSpanN(SortingContext ctx, int sz, long start, long end) throws IOException {
        if (sz == 1) {
            sortLargeSpan(ctx, start, end);
            return;
        }

        long size = end - start;

        if (size < ctx.memorySortLimit()) {
            quickSortN(sz, start, end);
        }
        else {
            mergeSortN(sz, start, end, ctx.tempDir());
        }
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



    default void insertionSort(long start, long end) {
        SortAlgoInsertionSort._insertionSort(this, start, end);
    }

    default void insertionSortN(int sz, long start, long end) {
        SortAlgoInsertionSort._insertionSortN(this, sz, start, end);
    }


    default void quickSort(long start, long end) {
        if (end - start < 64) {
            insertionSort(start, end);
        }
        else {
            SortAlgoQuickSort._quickSortLH(this, start, end - 1);
        }
    }

    default void quickSortN(int wordSize, long start, long end) {
        assert ((end - start) % wordSize) == 0;

        if (end == start)
            return;

        SortAlgoQuickSort._quickSortLHN(this, wordSize, start, end - wordSize);
    }

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
