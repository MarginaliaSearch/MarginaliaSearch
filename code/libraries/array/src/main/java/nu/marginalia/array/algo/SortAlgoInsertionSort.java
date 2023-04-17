package nu.marginalia.array.algo;

class SortAlgoInsertionSort {

    static void _insertionSortN(LongArraySort array, int sz, long start, long end) {

        assert end - start < Integer.MAX_VALUE;

        int span = (int) (end - start);

        assert (span % sz) == 0;

        if (span <= sz) {
            return;
        }

        for (int i = 1; i < span / sz; i++) {
            long key = array.get(start + (long) i * sz);

            int j;
            for (j = i - 1; j >= 0 && array.get(start + (long) j * sz) > key; j--) {
                array.swap(start + (long) j * sz, start + (long) (j + 1) * sz);
            }
            array.set(start + (long) (j + 1) * sz, key);
        }
    }

    static void _insertionSort(LongArraySort array, long start, long end) {
        assert end - start < Integer.MAX_VALUE;

        int n = (int) (end - start);

        if (n <= 1) {
            return;
        }

        for (int i = 1; i < n; i++) {
            long key = array.get(start + i);

            int j;
            for (j = i - 1; j >= 0 && array.get(start + j) > key; j--) {
                array.swap(start + j, start + j + 1);
            }
            array.set(start + j + 1, key);
        }
    }
}
