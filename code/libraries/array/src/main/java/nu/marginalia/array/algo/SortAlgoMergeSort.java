package nu.marginalia.array.algo;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

class SortAlgoMergeSort {

    static void _mergeSort(IntArraySort array, long start, int length, IntBuffer workBuffer) {
        int width = Math.min(Integer.highestOneBit(length), 1 << 16);

        // Do in-memory sorting up until internalSortLimit first
        for (int i = 0; i < length; i += width) {
            array.quickSort(start + i, start + i + Math.min(width, length-i));
        }

        // Then finish with merge sort
        for (width = 1; width < length; width*=2) {

            for (int i = 0; i < length; i += 2*width) {
                _merge(array, start, i, Math.min(i+width, length), Math.min(i+2*width, length), workBuffer);
            }

            workBuffer.clear();
            array.set(start, start + length, workBuffer, 0);
        }

    }




    static void _mergeSortN(LongArraySort array, int wordSize, long start, int length, LongBuffer workBuffer) throws IOException {
        int width = Math.min(Integer.highestOneBit(length), Integer.highestOneBit(workBuffer.capacity()));

        // Do in-memory sorting up until internalSortLimit first
        for (int i = 0; i < length; i += width) {
            array.quickSortN(wordSize, start + i, start + i + Math.min(width, length-i));
        }

        // Then finish with merge sort
        for (; width < length; width*=2) {

            for (int i = 0; i < length; i += 2*width) {
                _mergeN(array, wordSize, start, i, Math.min(i+width, length), Math.min(i+2*width, length), workBuffer);
            }

            workBuffer.clear();
            array.set(start, start + length, workBuffer, 0);
        }

    }

    static void _mergeSort(LongArraySort array, long start, int length, LongBuffer workBuffer) {
        int width = Math.min(Integer.highestOneBit(length), 1 << 16);

        // Do in-memory sorting up until internalSortLimit first
        for (int i = 0; i < length; i += width) {
            array.quickSort(start + i, start + i + Math.min(width, length-i));
        }

        // Then finish with merge sort
        for (width = 1; width < length; width*=2) {

            for (int i = 0; i < length; i += 2*width) {
                _merge(array, start, i, Math.min(i+width, length), Math.min(i+2*width, length), workBuffer);
            }

            workBuffer.clear();
            array.set(start, start + length, workBuffer, 0);
        }

    }


    static void _mergeN(LongArraySort array, int wordSize, long offset, int left, int right, int end, LongBuffer workBuffer) {
        long idxL = left;
        long idxR = right;

        for (int putPos = left; putPos < end; putPos+= wordSize) {

            if (idxL < right && (idxR >= end || array.get(offset+idxL) < array.get(offset+idxR))) {
                workBuffer.put(putPos, array.get(offset+idxL));
                for (int s = 1; s < wordSize; s++) {
                    workBuffer.put(putPos + s, array.get(offset + idxL + s));
                }
                idxL+= wordSize;
            }
            else {
                workBuffer.put(putPos, array.get(offset+idxR));
                for (int s = 1; s < wordSize; s++) {
                    workBuffer.put(putPos + s, array.get(offset + idxR + s));
                }
                idxR+= wordSize;
            }
        }
    }


    static void _merge(LongArraySort array, long offset, int left, int right, int end, LongBuffer workBuffer) {
        long idxL = left;
        long idxR = right;

        for (int putPos = left; putPos < end; putPos++) {
            if (idxL < right && (idxR >= end || array.get(offset+idxL) < array.get(offset+idxR))) {
                workBuffer.put(putPos, array.get(offset+idxL));
                idxL++;
            }
            else {
                workBuffer.put(putPos, array.get(offset+idxR));
                idxR++;
            }
        }
    }

    static void _merge(IntArraySort array, long offset, int left, int right, int end, IntBuffer workBuffer) {
        long idxL = left;
        long idxR = right;

        for (int putPos = left; putPos < end; putPos++) {
            if (idxL < right && (idxR >= end || array.get(offset+idxL) < array.get(offset+idxR))) {
                workBuffer.put(putPos, array.get(offset+idxL));
                idxL++;
            }
            else {
                workBuffer.put(putPos, array.get(offset+idxR));
                idxR++;
            }
        }
    }
}
