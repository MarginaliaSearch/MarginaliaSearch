package nu.marginalia.array.algo;

class SortAlgoInsertionSort {

    static void _insertionSortN(LongArraySort array, int sz, long start, long end) {

        assert end - start < Integer.MAX_VALUE;

        int span = (int) (end - start);

        assert (span % sz) == 0;

        if (span <= sz) {
            return;
        }

        long[] buf = new long[sz];
        for (long i = 1; i < span / sz; i++) {
            array.get(start + i * sz, buf);

            long key = buf[0];

            long j;
            for (j = i - 1; j >= 0 && array.get(start + j * sz) > key; j--) {
                shiftN(array, sz, start + j * sz, start + (j + 1) * sz, sz);
            }

            array.set(start + (j + 1) * sz, buf);
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
                shift(array, start + j, start + j + 1, 1);
            }

            array.set(start + j + 1, key);
        }
    }
    static void _insertionSort(IntArraySort array, long start, long end) {
        assert end - start < Integer.MAX_VALUE;

        int n = (int) (end - start);

        if (n <= 1) {
            return;
        }

        for (int i = 1; i < n; i++) {
            int key = array.get(start + i);

            int j;
            for (j = i - 1; j >= 0 && array.get(start + j) > key; j--) {
                shift(array, start + j, start + j + 1, 1);
            }

            array.set(start + j + 1, key);
        }
    }

    private static void shiftN(LongArraySort array, int sz, long start, long end, long shift) {
        for (long i = start; i < end; i+=sz) {
            for (int j = 0; j < sz; j++) {
                array.set(i + j + shift, array.get(i + j));
            }
        }
    }
    private static void shift(LongArraySort array, long start, long end, long shift) {
        for (long i = start; i < end; i++) {
            array.set(i + shift, array.get(i));
        }
    }

    private static void shift(IntArraySort array, long start, long end, long shift) {
        for (long i = start; i < end; i++) {
            array.set(i + shift, array.get(i));
        }
    }

}
