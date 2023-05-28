package nu.marginalia.array.algo;


class SortAlgoQuickSort {

    static void _quickSortLH(IntArraySort array, long low, long highInclusive) {

        if (low < 0 || highInclusive < 0 || low >= highInclusive)
            return;

        if (highInclusive - low < 32) {
            array.insertionSort(low, highInclusive + 1);
            return;
        }

        long p = _quickSortPartition(array, low, highInclusive);

        _quickSortLH(array, low, p);
        _quickSortLH(array, p + 1, highInclusive);
    }


    static long _quickSortPartition(IntArraySort array, long low, long high) {

        long pivotPoint = ((low + high) / (2L));
        int pivot = array.get(pivotPoint);

        long i = low - 1;
        long j = high + 1;

        for (;;) {
            do {
                i+=1;
            } while (array.get(i) < pivot);

            do {
                j-=1;
            }
            while (array.get(j) > pivot);

            if (i >= j) return j;
            else array.swap(i, j);
        }
    }


    static void _quickSortLHN(LongArraySort array, int wordSize, long low, long highInclusive) {
        if (low < 0 || highInclusive < 0 || low >= highInclusive)
            return;

        if (highInclusive - low < 32L*wordSize) {
            array.insertionSortN(wordSize, low, highInclusive + wordSize);
            return;
        }

        long p = _quickSortPartitionN(array, wordSize, low, highInclusive);

        _quickSortLHN(array, wordSize, low, p);
        _quickSortLHN(array, wordSize, p + wordSize, highInclusive);
    }


    static void _quickSortLH(LongArraySort array, long low, long highInclusive) {

        if (low < 0 || highInclusive < 0 || low >= highInclusive)
            return;

        if (highInclusive - low < 32) {
            array.insertionSort(low, highInclusive + 1);
            return;
        }

        long p = _quickSortPartition(array, low, highInclusive);

        _quickSortLH(array, low, p);
        _quickSortLH(array,p + 1, highInclusive);
    }


    static long _quickSortPartition(LongArraySort array, long low, long high) {

        long pivotPoint = low + ((high - low) / 2L);
        long pivot = array.get(pivotPoint);

        long i = low - 1;
        long j = high + 1;

        for (;;) {
            do {
                i+=1;
            } while (array.get(i) < pivot);

            do {
                j-=1;
            }
            while (array.get(j) > pivot);

            if (i >= j) return j;
            else array.swap(i, j);
        }
    }

    static long _quickSortPartitionN(LongArraySort array, int wordSize, long low, long high) {

        long delta = (high - low) / (2L);
        long pivotPoint = low + (delta / wordSize) * wordSize;

        long pivot = array.get(pivotPoint);

        assert (pivotPoint - low) >= 0;
        assert (pivotPoint - low) % wordSize == 0;


        long i = low - wordSize;
        long j = high + wordSize;

        for (;;) {
            do {
                i+=wordSize;
            }
            while (array.get(i) < pivot);

            do {
                j-=wordSize;
            }
            while (array.get(j) > pivot);

            if (i >= j) return j;
            else array.swapn(wordSize, i, j);
        }
    }

}
