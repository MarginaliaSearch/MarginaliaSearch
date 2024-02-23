package nu.marginalia.array.algo;

import nu.marginalia.array.IntArray;
import nu.marginalia.util.test.TestUtil;
import org.apache.commons.lang3.ArrayUtils;
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
class IntArraySortTest {

    IntArray basic;
    IntArray paged;
    IntArray shifted;

    final int size = 1026;

    @BeforeEach
    public void setUp() {
        basic = IntArray.allocate(size);
        paged = IntArray.allocate(size);
        shifted = IntArray.allocate(size+30).shifted(30);

        var random = new Random();
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = random.nextInt(0, 1000);
        }

        basic.transformEach(0, size, (i, old) -> values[(int) i]);
        paged.transformEach(0, size, (i, old) -> values[(int) i]);
        shifted.transformEach(0, size, (i, old) -> values[(int) i]);
    }

    interface SortOperation {
        void sort(IntArray array, long start, long end) throws IOException;
    }

    @Test
    public void quickSortStressTest() throws IOException {
        IntArray array = IntArray.allocate(65536);
        sortAlgorithmTester(array, IntArraySort::quickSort);
    }


    @Test
    public void insertionSortStressTest() throws IOException {
        IntArray array = IntArray.allocate(8192);
        sortAlgorithmTester(array, IntArraySort::insertionSort);
    }

    @Test
    public void mergeSortStressTest() throws IOException {
        IntArray array = IntArray.allocate(65536);
        Path tempDir = Files.createTempDirectory(getClass().getSimpleName());
        sortAlgorithmTester(array, (a, s, e) -> a.mergeSort(s, e, tempDir));
        TestUtil.clearTempDir(tempDir);
    }

    void sortAlgorithmTester(IntArray array, SortOperation operation) throws IOException {

        int[] values = new int[(int) array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }

        ArrayUtils.shuffle(values);

        int sentinelA = 0xFEEDBEEF;
        int sentinelB = 0xB000B000;

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
    void quickSort() {
        basic.quickSort(0, size);
        assertTrue(basic.isSorted(0, size));

        paged.quickSort(0, size);
        assertTrue(paged.isSorted(0, size));

        shifted.quickSort(0, size);
        assertTrue(shifted.isSorted(0, 128));
    }

    @Test
    void mergeSort() throws IOException {
        basic.mergeSort(0, size, Path.of("/tmp"));
        assertTrue(basic.isSorted(0, size));

        paged.mergeSort(0, size, Path.of("/tmp"));
        assertTrue(paged.isSorted(0, size));

        shifted.mergeSort(0, size, Path.of("/tmp"));
        assertTrue(shifted.isSorted(0, 128));
    }
}