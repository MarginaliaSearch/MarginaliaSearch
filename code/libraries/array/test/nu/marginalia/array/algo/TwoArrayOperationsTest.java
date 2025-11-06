package nu.marginalia.array.algo;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TwoArrayOperationsTest {

    @Test
    void mergeArrays() {
        LongArray a = LongArrayFactory.onHeapShared(10);
        LongArray b = LongArrayFactory.onHeapShared(15);

        a.set(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        b.set(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30);

        LongArray out = LongArrayFactory.onHeapShared(TwoArrayOperations.countDistinctElements(a, b, 0, 10, 0, 15));
        assertEquals(out.size(), TwoArrayOperations.mergeArraysN(1, out, a, b, 0, 0, 10, 0, 15));

        long[] values = new long[15];
        out.get(0, 15, values);

        assertArrayEquals(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 20}, values);
    }

    @Test
    @Tag("slow")
    public void mergeArraysFuzz() {

        long seed = System.nanoTime();
        for (int i = 0; i < 100_000; i++, seed++) {
            Random r = new Random(seed);
            int aLen = r.nextInt(1, 25);
            int aSkip = r.nextInt(0, aLen);
            int bLen = r.nextInt(1, 25);
            int bSkip = r.nextInt(0, bLen);
            int outSkip = r.nextInt(0, 25);

            LongList aVals = new LongArrayList(LongStream.generate(() -> r.nextLong(0, 25000)).limit(aLen).sorted().distinct().toArray());
            LongList bVals = new LongArrayList(LongStream.generate(() -> r.nextLong(0, 25000)).limit(bLen).sorted().distinct().toArray());
            LongList expectedOutVals = new LongArrayList(LongStream.concat(aVals.longStream().skip(aSkip), bVals.longStream().skip(bSkip)).sorted().distinct().toArray());

            try (var a = LongArrayFactory.onHeapShared(aVals);
                 var b = LongArrayFactory.onHeapShared(bVals);
                 var out = LongArrayFactory.onHeapShared(outSkip + aVals.size() + bVals.size())
            ) {
                long rv1 = TwoArrayOperations.mergeArraysN(1, out, a, b, outSkip, aSkip, a.size(), bSkip, b.size());
                LongList outcome1 = arrayToList(out.range(outSkip, outSkip+rv1));
                long rv2 = TwoArrayOperations.mergeArraysN(1,  out, a, b, outSkip, aSkip, a.size(), bSkip, b.size());
                LongList outcome2 = arrayToList(out.range(outSkip, outSkip+rv2));

                if (expectedOutVals.equals(outcome1) && expectedOutVals.equals(outcome2) && expectedOutVals.size() == rv1 && expectedOutVals.size() == rv2)
                    continue;

                System.out.println("Seed: " + seed);

                System.out.println(expectedOutVals.size());
                System.out.println(expectedOutVals);

                System.out.println(rv1);
                System.out.println(outcome1);

                System.out.println(rv2);
                System.out.println(outcome2);

                System.out.println(aSkip+":"+aLen+ ": " + aVals);
                System.out.println(bSkip+":"+bLen+ ": " + bVals);

                Assertions.assertEquals(expectedOutVals, outcome1);
                Assertions.assertEquals(expectedOutVals, outcome2);
                Assertions.assertEquals(expectedOutVals.size(), rv1);
                Assertions.assertEquals(expectedOutVals.size(), rv2);
            }
        }
    }


    @Test
    @Tag("slow")
    public void countDistinctFuzz() {

        long seed = System.nanoTime();
        for (int i = 0; i < 100_000; i++, seed++) {
            Random r = new Random(seed);
            int aLen = r.nextInt(1, 25);
            int aSkip = r.nextInt(0, aLen);
            int bLen = r.nextInt(1, 25);
            int bSkip = r.nextInt(0, bLen);

            LongList aVals = new LongArrayList(LongStream.generate(() -> r.nextLong(0, 25000)).limit(aLen).sorted().distinct().toArray());
            LongList bVals = new LongArrayList(LongStream.generate(() -> r.nextLong(0, 25000)).limit(bLen).sorted().distinct().toArray());
            long expectedOutCnt = LongStream.concat(aVals.longStream().skip(aSkip), bVals.longStream().skip(bSkip)).distinct().count();

            try (var a = LongArrayFactory.onHeapShared(aVals);
                 var b = LongArrayFactory.onHeapShared(bVals)) {
                long rv1 = TwoArrayOperations.countDistinctElements(a, b, aSkip, a.size(), bSkip, b.size());
                long rv2 = TwoArrayOperations.countDistinctElementsJava(a, b, aSkip, a.size(), bSkip, b.size());

                if (rv1 == expectedOutCnt && rv2 == expectedOutCnt)
                    continue;

                System.out.println("Seed: " + seed);

                System.out.println(rv1);
                System.out.println(expectedOutCnt);

                System.out.println(rv2);
                System.out.println(expectedOutCnt);

                System.out.println(aSkip+":"+aLen+ ": " + aVals);
                System.out.println(bSkip+":"+bLen+ ": " + bVals);

                Assertions.assertEquals(expectedOutCnt, rv1);
                Assertions.assertEquals(expectedOutCnt, rv2);
            }
        }
    }

    LongList arrayToList(LongArray array) {
        long[] arr = new long[(int) array.size()];
        array.get(0, arr);
        return new LongArrayList(arr);
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
    public void testCountMerge() {
        LongArray a = LongArrayFactory.onHeapShared(1024);
        LongArray b = LongArrayFactory.onHeapShared(512);
        LongArray c = LongArrayFactory.onHeapShared(1024 + 512);
        a.transformEach(0, 1024, (i, v) -> 4 * i);
        b.transformEach(0, 512, (i, v) -> 3 * i);


        long distinctSize = TwoArrayOperations.countDistinctElements(a, b, 0, 1024, 0, 512);

        long mergedSize = TwoArrayOperations.mergeArraysN(1, c, a, b, 0, 0, 1024, 0, 512);

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

        assertEquals(out.size(), TwoArrayOperations.mergeArrays2(out, left, right, 0, 0, 4, 0, 2));

        System.out.println(Arrays.toString(longArrayToJavaArray(out)));

    }

    long[] longArrayToJavaArray(LongArray longArray) {
        long[] vals = new long[(int) longArray.size()];
        longArray.get(0, vals);
        return vals;
    }


    @Test
    void mergeArrays3__overlap_left() {
        LongArray left = LongArrayFactory.onHeapShared(6);
        LongArray right = LongArrayFactory.onHeapShared(3);
        LongArray out = LongArrayFactory.onHeapShared(6);
        LongArray out9 = LongArrayFactory.onHeapShared(9);

        left.set(0, 40, 3, -3, 41, 4, -4);
        right.set(0, 40, 5, -5);
        long numDistinct = TwoArrayOperations.countDistinctElementsN(3, left, right, 0, 6, 0, 3);

        // merge l, r
        long ret = TwoArrayOperations.mergeArrays3(out, left, right, 0, 0, 6, 0, 3);
        assertEquals(numDistinct * 3, ret);

        long[] expected = new long[]{40, 3, -3, 41, 4, -4};
        long[] actual = longArrayToJavaArray(out);

        System.out.println(Arrays.toString(expected));
        System.out.println(Arrays.toString(actual));

        assertArrayEquals(expected, actual);
    }

    @Test
    void mergeArrays3__overlap_right() {
        LongArray left = LongArrayFactory.onHeapShared(6);
        LongArray right = LongArrayFactory.onHeapShared(3);
        LongArray out = LongArrayFactory.onHeapShared(6);
        LongArray out9 = LongArrayFactory.onHeapShared(9);

        left.set(0, 40, 3, -3, 41, 4, -4);
        right.set(0, 40, 5, -5);
        long numDistinct = TwoArrayOperations.countDistinctElementsN(3, left, right, 0, 6, 0, 3);

        // merge l, r
        long ret = TwoArrayOperations.mergeArrays3(out, right, left, 0, 0, 3, 0, 6);
        assertEquals(numDistinct * 3, ret);

        long[] expected = new long[] { 40, 5, -5, 41, 4, -4 };
        long[] actual = longArrayToJavaArray(out);

        System.out.println(Arrays.toString(expected));
        System.out.println(Arrays.toString(actual));

        assertArrayEquals(expected, actual);
    }

    @Test
    void mergeArrays3__distinct_2_1() {
        LongArray left = LongArrayFactory.onHeapShared(6);
        LongArray right = LongArrayFactory.onHeapShared(3);
        LongArray out = LongArrayFactory.onHeapShared(9);

        left.set(0, 40, 3, -3, 41, 4, -4);
        right.set(0, 39, 5, -5);

        long numDistinct = TwoArrayOperations.countDistinctElementsN(3, left, right, 0, 6, 0, 3);

        // merge l, r
        long ret = TwoArrayOperations.mergeArrays3(out, left, right, 0, 0, 6, 0, 3);
        assertEquals(numDistinct * 3, ret);

        long[] expected = new long[] { 39, 5, -5, 40, 3, -3, 41, 4, -4 };
        long[] actual = longArrayToJavaArray(out);

        System.out.println(Arrays.toString(expected));
        System.out.println(Arrays.toString(actual));

        assertArrayEquals(expected, actual);
    }

    @Test
    void mergeArrays3__distinct_1_2() {
        LongArray left = LongArrayFactory.onHeapShared(6);
        LongArray right = LongArrayFactory.onHeapShared(3);
        LongArray out = LongArrayFactory.onHeapShared(9);

        left.set(0, 40, 3, -3, 41, 4, -4);
        right.set(0, 39, 5, -5);

        long numDistinct = TwoArrayOperations.countDistinctElementsN(3, left, right, 0, 6, 0, 3);

        // merge l, r
        long ret = TwoArrayOperations.mergeArrays3(out, left, right, 0, 0, 6, 0, 3);
        assertEquals(numDistinct * 3, ret);

        long[] expected = new long[] { 39, 5, -5, 40, 3, -3, 41, 4, -4 };
        long[] actual = longArrayToJavaArray(out);

        System.out.println(Arrays.toString(expected));
        System.out.println(Arrays.toString(actual));

        assertArrayEquals(expected, actual);
    }

}