package nu.marginalia.array.algo;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.util.test.TestUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
class LongArraySortNTest {

    LongArray basic;
    LongArray shifted;
    LongArray segment;

    Long2ObjectOpenHashMap<LongOpenHashSet> dataAsPairs;

    final int size = 1026;

    @BeforeEach
    public void setUp() {
        basic = LongArray.allocate(size);
        shifted = LongArray.allocate(size+30).shifted(30);
        segment = LongArrayFactory.onHeapShared(size + 30).shifted(30);

        var random = new Random();
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            values[i] = random.nextInt(0, 1000);
        }
        for (int i = 1; i < size; i+=2) {
            values[i] = -values[i];
        }

        basic.set(0, values);
        shifted.set(0, values);
        segment.set(0, values);

        dataAsPairs = asPairs(basic);
    }


    interface SortOperation {
        void sort(LongArray array, long start, long end) throws IOException;
    }

    @Test
    public void quickSortStressTest() throws IOException {
        LongArray array = LongArray.allocate(65536);
        sortAlgorithmTester(array, LongArraySort::sort);
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
        long sentinelB = 0xB000B000L;

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

    private void compare(LongArray sorted, Long2ObjectOpenHashMap<LongOpenHashSet> expectedPairs) {
        var actual = asPairs(sorted);

        assertEquals(expectedPairs, actual);
    }

    @Test
    void insertionSortN() {
        LongArraySort.insertionSortN(basic, 2, 0, size);
        assertTrue(basic.isSortedN(2, 0, size));

        LongArraySort.insertionSortN(shifted, 2, 0, size);
        assertTrue(shifted.isSortedN(2, 0, size));

        LongArraySort.insertionSortN(segment, 2, 0, size);
        assertTrue(segment.isSortedN(2, 0, size));

        compare(basic, dataAsPairs);
        compare(shifted, dataAsPairs);
        compare(segment, dataAsPairs);
    }

    @Test
    void quickSortN() {
        basic.quickSortN(2, 0, size);
        assertTrue(basic.isSortedN(2, 0, size));

        shifted.quickSortN(2, 0, size);
        assertTrue(shifted.isSortedN(2, 0, size));

        segment.quickSortN(2, 0, size);
        assertTrue(segment.isSortedN(2, 0, size));

        compare(basic, dataAsPairs);
        compare(shifted, dataAsPairs);
        compare(segment, dataAsPairs);
    }

    @Test
    void mergeSortN() throws IOException {

        basic.mergeSortN(2, 0, size, Path.of("/tmp"));
        assertTrue(basic.isSortedN(2, 0, size));

        shifted.mergeSortN(2, 0, size, Path.of("/tmp"));
        assertTrue(shifted.isSortedN(2, 0, size));

        segment.mergeSortN(2, 0, size, Path.of("/tmp"));
        assertTrue(segment.isSortedN(2, 0, size));

        compare(basic, dataAsPairs);
        compare(shifted, dataAsPairs);
        compare(segment, dataAsPairs);
    }

    private Long2ObjectOpenHashMap<LongOpenHashSet> asPairs(LongArray array) {
        Long2ObjectOpenHashMap<LongOpenHashSet> map = new Long2ObjectOpenHashMap<>();
        for (long i = 0; i < array.size(); i+=2) {
            long key = array.get(i);
            long val = array.get(i+1);
            if (null == map.get(key)) {
                var set = new LongOpenHashSet();
                map.put(key, set);
            }
            map.get(key).add(val);
        }
        return map;
    }


}