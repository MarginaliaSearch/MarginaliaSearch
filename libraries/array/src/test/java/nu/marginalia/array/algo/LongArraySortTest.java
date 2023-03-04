package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.page.LongArrayPage;
import nu.marginalia.array.page.PagingLongArray;
import nu.marginalia.array.scheme.PowerOf2PartitioningScheme;
import nu.marginalia.util.test.TestUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
class LongArraySortTest {

    LongArray basic;
    LongArray paged;
    LongArray shifted;
    final int size = 1026;

    @BeforeEach
    public void setUp() {
        basic = LongArrayPage.onHeap(size);
        paged = PagingLongArray.newOnHeap(new PowerOf2PartitioningScheme(32), size);
        shifted = LongArrayPage.onHeap(size + 30).shifted(30);

        var random = new Random();
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            values[i] = random.nextInt(0, 1000);
        }

        basic.transformEach(0, size, (i, old) -> values[(int) i]);
        paged.transformEach(0, size, (i, old) -> values[(int) i]);
        shifted.transformEach(0, size, (i, old) -> values[(int) i]);
    }

    interface SortOperation {
        void sort(LongArray array, long start, long end) throws IOException;
    }

    @Test
    public void quickSortStressTest() throws IOException {
        LongArray array = LongArray.allocate(65536);
        sortAlgorithmTester(array, LongArraySort::quickSort);
    }


    @Test
    public void insertionSortStressTest() throws IOException {
        LongArray array = LongArray.allocate(8192);
        sortAlgorithmTester(array, LongArraySort::insertionSort);
    }

    @Test
    public void mergeSortStressTest() throws IOException {
        LongArray array = LongArray.allocate(65536);
        Path tempDir = Files.createTempDirectory(getClass().getSimpleName());
        sortAlgorithmTester(array, (a, s, e) -> a.mergeSort(s, e, tempDir));
        TestUtil.clearTempDir(tempDir);
    }

    void sortAlgorithmTester(LongArray array, SortOperation operation) throws IOException {

        long[] values = new long[(int) array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        ArrayUtils.shuffle(values);

        long sentinelA = 0xFEEDBEEFL;
        long sentinelB = 0xB000B000;

        int start = 6;
        for (int end = start + 1; end < values.length - 1; end+=97) {

            // Use sentinel values to catch if the sort algorithm overwrites end values
            array.set(start - 1, sentinelA);
            array.set(end, sentinelB);

            long orderInvariantChecksum = 0;
            for (long i = 0; i < end - start; i++) {
                array.set(start + i, values[start + (int)i]);

                // Try to checksum the contents to catch bugs where the result is sorted
                // but a value has been duplicated, overwriting another
                orderInvariantChecksum ^= values[start + (int)i];
            }

            operation.sort(array, start, end);

            assertTrue(array.isSorted(start, end), "Array wasn't sorted");

            assertEquals(sentinelA, array.get(start - 1), "Start position sentinel overwritten");
            assertEquals(sentinelB, array.get(end), "End position sentinel overwritten");

            long actualChecksum = 0;
            for (long i = start; i < end; i++) {
                actualChecksum ^= array.get(i);
            }

            assertEquals(orderInvariantChecksum, actualChecksum, "Checksum validation failed");
        }

    }

    @Test
    void insertionSort() {
        basic.insertionSort(0, size);
        assertTrue(basic.isSorted(0, 128));

        paged.insertionSort(0, size);
        assertTrue(paged.isSorted(0, 128));

        shifted.insertionSort(0, size);
        assertTrue(shifted.isSorted(0, 128));
    }

    @Test
    void insertionSortN() {
        basic.insertionSortN(2, 0, size);
        assertTrue(basic.isSortedN(2, 0, size));

        paged.insertionSortN(2, 0, size);
        assertTrue(paged.isSortedN(2, 0, size));

        shifted.insertionSortN(2, 0, size);
        assertTrue(shifted.isSortedN(2, 0, size));
    }

    @Test
    void quickSort() {
        basic.quickSort(0, size);
        assertTrue(basic.isSorted(0, size));

        paged.quickSort(0, size);
        assertTrue(paged.isSorted(0, size));

        shifted.quickSort(0, size);
        assertTrue(shifted.isSorted(0, size));
    }

    @Test
    void quickSortN() {
        basic.quickSortN(2, 0, size);
        assertTrue(basic.isSortedN(2, 0, size));

        paged.quickSortN(2, 0, size);
        assertTrue(paged.isSortedN(2, 0, size));

        shifted.quickSortN(2, 0, size);
        assertTrue(shifted.isSortedN(2, 0, size));
    }

    @Test
    void mergeSortN() throws IOException {
        basic.mergeSortN(2, 0, size, Path.of("/tmp"));
        assertTrue(basic.isSortedN(2, 0, size));

        paged.mergeSortN(2, 0, size, Path.of("/tmp"));
        assertTrue(paged.isSortedN(2, 0, size));

        shifted.mergeSortN(2, 0, size, Path.of("/tmp"));
        assertTrue(shifted.isSortedN(2, 0, size));
    }

    @Test
    void mergeSort() throws IOException {
        basic.mergeSort(0, size, Path.of("/tmp"));
        assertTrue(basic.isSorted(0, size));

        paged.mergeSort(0, size, Path.of("/tmp"));
        assertTrue(paged.isSorted(0, size));

        shifted.mergeSort(0, size, Path.of("/tmp"));
        assertTrue(shifted.isSorted(0, size));
    }
}