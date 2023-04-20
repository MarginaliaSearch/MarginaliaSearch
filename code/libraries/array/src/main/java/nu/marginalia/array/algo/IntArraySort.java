package nu.marginalia.array.algo;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public interface IntArraySort extends IntArrayBase {

    default boolean isSorted(long start, long end) {
        if (start == end) return true;

        int val = get(start);
        for (long i = start + 1; i < end; i++) {
            int next = get(i);
            if (next < val)
                return false;
            val = next;
        }

        return true;
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

    default boolean isSortedN(int wordSize, long start, long end) {
        if (start == end) return true;

        int val = get(start);
        for (long i = start + wordSize; i < end; i+=wordSize) {
            int next = get(i);
            if (next < val)
                return false;
            val = next;
        }

        return true;
    }



    default void insertionSort(long start, long end) {
        assert end - start < Integer.MAX_VALUE;

        int n = (int) (end - start);

        if (n <= 1) {
            return;
        }

        for (int i = 1; i < n; i++) {
            int key = get(start + i);

            int j = i - 1;
            while (j >= 0 && get(start + j) > key) {
                swap( start + j, start + (long)(j+1));
                j--;
            }
            set(start + j+1, key);
        }
    }

    default void quickSort(long start, long end) {
        if (end - start < 64) {
            insertionSort(start, end);
        }
        else {
            SortAlgoQuickSort._quickSortLH(this, start, end - 1);
        }
    }

    default void mergeSort(long start, long end, Path tmpDir) throws IOException {
        int length = (int) (end - start);

        Path tmpFile = Files.createTempFile(tmpDir,"sort-"+start+"-"+(start+length), ".dat");
        try (var channel = (FileChannel) Files.newByteChannel(tmpFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            var workBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 4L * length).asIntBuffer();

            SortAlgoMergeSort._mergeSort(this, start, length, workBuffer);
        }
        finally {
            Files.delete(tmpFile);
        }
    }

}
