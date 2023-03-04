package nu.marginalia.array.algo;

import java.io.IOException;
import java.nio.LongBuffer;
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
        assert end - start < Integer.MAX_VALUE;

        int n = (int) (end - start);

        if (n <= 1) {
            return;
        }

        for (int i = 1; i < n; i++) {
            long key = get(start + i);

            int j = i - 1;
            while (j >= 0 && get(start + j) > key) {
                swap( start + j, start + (long)(j+1));
                j--;
            }
            set(start + j+1, key);
        }
    }

    default void insertionSortN(int sz, long start, long end) {
        assert end - start < Integer.MAX_VALUE;

        int span = (int) (end - start);

        assert (span % sz) == 0;

        if (span <= sz) {
            return;
        }

        for (int i = 1; i < span / sz; i++) {
            long key = get(start + (long) i * sz);

            int j = i - 1;
            while (j >= 0 && get(start + (long)sz*j) > key) {
                swapn(sz, start + (long)sz*j, start + (long)sz*(j+1));
                j--;
            }
            set(start + (long) (j+1) * sz, key);
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

    default void quickSortN(int wordSize, long start, long end) {
        assert ((end - start) % wordSize) == 0;

        if (end == start)
            return;

        _quickSortLHN(wordSize, start, end - wordSize);
    }

    default void _quickSortLHN(int wordSize, long low, long highInclusive) {
        if (low < 0 || highInclusive < 0 || low >= highInclusive)
            return;

        if (highInclusive - low < 32L*wordSize) {
            insertionSortN(wordSize, low, highInclusive + wordSize);
            return;
        }

        long p = _quickSortPartitionN(wordSize, low, highInclusive);

        _quickSortLHN(wordSize, low, p);
        _quickSortLHN(wordSize, p + wordSize, highInclusive);
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
        long pivot = get(pivotPoint);

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

    default long _quickSortPartitionN(int wordSize, long low, long high) {

        long pivotPoint = ((low + high) / (2L*wordSize)) * wordSize;
        long pivot = get(pivotPoint);

        long i = low - wordSize;
        long j = high + wordSize;

        for (;;) {
            do {
                i+=wordSize;
            }
            while (get(i) < pivot);

            do {
                j-=wordSize;
            }
            while (get(j) > pivot);

            if (i >= j) return j;
            else swapn(wordSize, i, j);
        }
    }

    default void _mergeSortN(int wordSize, long start, int length, LongBuffer workBuffer) throws IOException {
        int width = Math.min(Integer.highestOneBit(length), Integer.highestOneBit(workBuffer.capacity()));

        // Do in-memory sorting up until internalSortLimit first
        for (int i = 0; i < length; i += width) {
            quickSortN(wordSize, start + i, start + i + Math.min(width, length-i));
        }

        // Then finish with merge sort
        for (; width < length; width*=2) {

            for (int i = 0; i < length; i += 2*width) {
                _mergeN(wordSize, start, i, Math.min(i+width, length), Math.min(i+2*width, length), workBuffer);
            }

            workBuffer.clear();
            set(start, start + length, workBuffer, 0);
        }

    }

    default void mergeSortN(int wordSize, long start, long end, Path tmpDir) throws IOException {
        int length = (int) (end - start);
        assert (length % wordSize) == 0;

        Path tmpFile = Files.createTempFile(tmpDir,"sort-"+start+"-"+(start+length), ".dat");
        try (var channel = (FileChannel) Files.newByteChannel(tmpFile, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            var workBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 8L * length).asLongBuffer();

            _mergeSortN(wordSize, start, length, workBuffer);
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

            _mergeSort(start, length, workBuffer);
        }
        finally {
            Files.delete(tmpFile);
        }

    }

    default void _mergeSort(long start, int length, LongBuffer workBuffer) {
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


    default void _mergeN(int wordSize, long offset, int left, int right, int end, LongBuffer workBuffer) {
        long idxL = left;
        long idxR = right;

        for (int putPos = left; putPos < end; putPos+= wordSize) {

            if (idxL < right && (idxR >= end || get(offset+idxL) < get(offset+idxR))) {
                workBuffer.put(putPos, get(offset+idxL));
                for (int s = 1; s < wordSize; s++) {
                    workBuffer.put(putPos + s, get(offset + idxL + s));
                }
                idxL+= wordSize;
            }
            else {
                workBuffer.put(putPos, get(offset+idxR));
                for (int s = 1; s < wordSize; s++) {
                    workBuffer.put(putPos + s, get(offset + idxR + s));
                }
                idxR+= wordSize;
            }
        }
    }


    default void _merge(long offset, int left, int right, int end, LongBuffer workBuffer) {
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
