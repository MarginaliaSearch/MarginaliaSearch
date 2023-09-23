package nu.marginalia.array.algo;

import com.google.common.collect.Sets;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class TwoArrayOperationsTest {

    @Test
    void mergeArrays() {
        LongArray a = LongArrayFactory.onHeapShared(10);
        LongArray b = LongArrayFactory.onHeapShared(15);

        a.set(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        b.set(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30);

        LongArray out = LongArrayFactory.onHeapShared(TwoArrayOperations.countDistinctElements(a, b, 0, 10, 0, 15));
        TwoArrayOperations.mergeArrays(out, a, b, 0, out.size(), 0, 10, 0, 15);

        long[] values = new long[15];
        out.get(0, 15, values);

        assertArrayEquals(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 20}, values);
    }

    @Test
    void countDistinctElements() {
        LongArray a = LongArrayFactory.onHeapShared(10);
        LongArray b = LongArrayFactory.onHeapShared(15);

        long[] aVals = new long[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        long[] bVals = new long[] { 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30 };

        a.set(0, aVals);
        b.set(0, bVals);

        int expected = Sets.union(
                LongStream.of(aVals).boxed().collect(Collectors.toSet()),
                LongStream.of(bVals).boxed().collect(Collectors.toSet())
        ).size();

        assertEquals(expected, TwoArrayOperations.countDistinctElements(a, b, 0, 10, 0, 15));

        expected = Sets.union(
                LongStream.of(aVals).skip(5).limit(10-5).boxed().collect(Collectors.toSet()),
                LongStream.of(bVals).skip(5).limit(10-5).boxed().collect(Collectors.toSet())
        ).size();

        assertEquals(expected, TwoArrayOperations.countDistinctElements(a, b, 5, 10, 5, 10));

        expected = Sets.union(
                LongStream.of(aVals).skip(5).limit(0).boxed().collect(Collectors.toSet()),
                LongStream.of(bVals).skip(0).limit(15).boxed().collect(Collectors.toSet())
        ).size();

        assertEquals(expected, TwoArrayOperations.countDistinctElements(a, b, 5, 5, 0, 15));
    }

    @Test
    void mergeArrayValues() {
        // create two arrays with associated values
        // these must be sorted in the odd positions

        long[] aVals = new long[] { 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10 };
        long[] bVals = new long[] { 2, 1, 4, 2, 6, 3, 8, 4, 10, 5, 12, 6, 14, 7, 16, 8, 18, 9, 20, 10, 22, 11, 24, 12, 26, 13, 28, 14, 30, 15 };

        LongArray a = LongArrayFactory.onHeapShared(20);
        LongArray b = LongArrayFactory.onHeapShared(30);

        a.set(0, aVals);
        b.set(0, bVals);

        // merge b's associated values into a
        TwoArrayOperations.mergeArrayValues(a, b, Long::sum, 0, 20, 0, 30);

        // fetch the values back into aVals
        a.get(0, 20, aVals);

        var map = new HashMap<Long, Long>();
        for (int i = 0; i < aVals.length; i+=2) {
            map.put(aVals[i], aVals[i+1]);
        }

        // aVals contained the keys 1..10, and bVals contained the keys 2..30
        // aVals' values were the same as the keys, but bVals' values were half the keys' values
        // the merged values should be the sum of the two values in even positions,
        // and the same as the keys in odd positions
        map.forEach((k,v) -> {
            if (k % 2 == 0) {
                Assertions.assertEquals(2 * v, 3*k);
            }
            else {
                Assertions.assertEquals(v, k);
            }
        });
    }

    @Test
    public void testCountMerge() {
        LongArray a = LongArrayFactory.onHeapShared(1024);
        LongArray b = LongArrayFactory.onHeapShared(512);
        LongArray c = LongArrayFactory.onHeapShared(1024 + 512);
        a.transformEach(0, 1024, (i, v) -> 4 * i);
        b.transformEach(0, 512, (i, v) -> 3 * i);


        long distinctSize = TwoArrayOperations.countDistinctElements(a, b, 0, 1024, 0, 512);

        long mergedSize = TwoArrayOperations.mergeArrays(c, a, b, 0, 1024+512, 0, 1024, 0, 512);

        assertEquals(distinctSize, mergedSize);

    }

    @Test
    public void mergeArrays2() {
        LongArray left = LongArrayFactory.onHeapShared(4);
        LongArray right = LongArrayFactory.onHeapShared(2);
        LongArray out = LongArrayFactory.onHeapShared(4);
        left.set(0, 40, 3, 41, 4);
        right.set(0, 40, 5);

        System.out.println(Arrays.toString(longArrayToJavaArray(left)));
        System.out.println(Arrays.toString(longArrayToJavaArray(right)));
        System.out.println(Arrays.toString(longArrayToJavaArray(out)));
        long numDistinct = TwoArrayOperations.countDistinctElementsN(2, left, right, 0, 4, 0, 2);
        System.out.println(numDistinct);
        System.out.println(numDistinct);

        TwoArrayOperations.mergeArrays2(out, left, right, 0, 4, 0, 4, 0, 2);

        System.out.println(Arrays.toString(longArrayToJavaArray(out)));

    }

    long[] longArrayToJavaArray(LongArray longArray) {
        long[] vals = new long[(int) longArray.size()];
        longArray.get(0, vals);
        return vals;
    }
}