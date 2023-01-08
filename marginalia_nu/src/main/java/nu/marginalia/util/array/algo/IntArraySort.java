package nu.marginalia.util.array.algo;

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
            _quickSortLH(start, end - 1);
        }
    }

    default void _quickSortLH(long low, long highInclusive) {

        if (low < 0 || highInclusive < 0 || low >= highInclusive)
            return;

        if (highInclusive - low < 32) {
            insertionSort(low, highInclusive + 1);
            return;
        }

        long p = _quickSortPartition(low, highInclusive);

        _quickSortLH(low, p);
        _quickSortLH(p + 1, highInclusive);
    }


    default long _quickSortPartition(long low, long high) {

        long pivotPoint = ((low + high) / (2L));
        int pivot = get(pivotPoint);

        long i = low - 1;
        long j = high + 1;

        for (;;) {
            do {
                i+=1;
            } while (get(i) < pivot);

            do {
                j-=1;
            }
            while (get(j) > pivot);

            if (i >= j) return j;
            else swap(i, j);
        }
    }

    default void mergeSort(long start, long end, Path tmpDir) throws IOException {
        int length = (int) (end - start);

        Path tmpFile = Files.createTempFile(tmpDir,"sort-"+start+"-"+(start+length), ".dat");
        try (var channel = (FileChannel) Files.newByteChannel(tmpFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            var workBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 4L * length).asIntBuffer();

            _mergeSort(start, length, workBuffer);
        }
        finally {
            Files.delete(tmpFile);
        }
    }

    default void _mergeSort(long start, int length, IntBuffer workBuffer) {
        int width = Math.min(Integer.highestOneBit(length), 1 << 16);

        // Do in-memory sorting up until internalSortLimit first
        for (int i = 0; i < length; i += width) {
            quickSort(start + i, start + i + Math.min(width, length-i));
        }

        // Then finish with merge sort
        for (width = 1; width < length; width*=2) {

            for (int i = 0; i < length; i += 2*width) {
                _merge(start, i, Math.min(i+width, length), Math.min(i+2*width, length), workBuffer);
            }

            workBuffer.clear();
            set(start, start + length, workBuffer, 0);
        }

    }


    default void _merge(long offset, int left, int right, int end, IntBuffer workBuffer) {
        long idxL = left;
        long idxR = right;

        for (int putPos = left; putPos < end; putPos++) {
            if (idxL < right && (idxR >= end || get(offset+idxL) < get(offset+idxR))) {
                workBuffer.put(putPos, get(offset+idxL));
                idxL++;
            }
            else {
                workBuffer.put(putPos, get(offset+idxR));
                idxR++;
            }
        }
    }
}
