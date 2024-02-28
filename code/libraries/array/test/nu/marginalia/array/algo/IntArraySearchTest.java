package nu.marginalia.array.algo;

import nu.marginalia.array.IntArray;
import nu.marginalia.array.buffer.IntQueryBuffer;
import nu.marginalia.array.page.SegmentIntArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntArraySearchTest {

    IntArray basicArray = IntArray.allocate(1024);
    IntArray pagingArray = SegmentIntArray.onHeap(Arena.global(), 1024);

    IntArray shiftedArray = IntArray.allocate(1054).range(30, 1054);

    @BeforeEach
    public void setUp() {
        for (int i = 0; i < basicArray.size(); i++) {
            basicArray.set(i, 3*i);
            pagingArray.set(i, 3*i);
            shiftedArray.set(i, 3*i);
        }
    }

    @Test
    void linearSearch() {
        linearSearchTester(basicArray);
        linearSearchTester(pagingArray);
        linearSearchTester(shiftedArray);
    }

    @Test
    void binarySearch() {
        binarySearchTester(basicArray);
        binarySearchTester(pagingArray);
        binarySearchTester(shiftedArray);
    }

    @Test
    void binarySearchUpperbound() {
        binarySearchUpperBoundTester(basicArray);
        binarySearchUpperBoundTester(pagingArray);
        binarySearchUpperBoundTester(shiftedArray);
    }

    void linearSearchTester(IntArray array) {
        for (int i = 0; i < array.size() * 3; i++) {
            long ret = array.linearSearch(i, 0, array.size());

            if ((i % 3) == 0) {
                assertTrue(ret >= 0);
                assertEquals(i, array.get(ret));
            }
            else {
                long higher = LongArraySearch.decodeSearchMiss(1, ret);
                if (i > 0 && higher < array.size()) {
                    assertTrue(array.get(higher) < i);
                }
            }
        }
    }

    void binarySearchTester(IntArray array) {
        for (int i = 0; i < array.size() * 3; i++) {
            long ret = array.binarySearch(i, 0, array.size());

            if ((i % 3) == 0) {
                assertTrue(ret >= 0);
                assertEquals(i, array.get(ret));
            }
            else {
                long higher = LongArraySearch.decodeSearchMiss(1, ret);
                if (i > 0 && higher+1 < array.size()) {
                    assertTrue(array.get(higher) < i);
                }
            }
        }
    }

    void binarySearchUpperBoundTester(IntArray array) {
        for (int i = 0; i < array.size() * 3; i++) {
            long ret = array.binarySearchUpperBound(i, 0, array.size());

            if ((i % 3) == 0) {
                assertTrue(ret >= 0);
                assertEquals(i, array.get(ret));
            }
            else {
                if (i > 0 && ret > 0 && ret < array.size()) {
                    assertTrue(array.get(ret-1) < i);
                }
            }
        }
    }

    @Test
    void retain() {
        int[] vals = new int[128];
        for (int i = 0; i < vals.length; i++) { vals[i] = i; }
        var buffer = new IntQueryBuffer(vals, 128);

        basicArray.retain(buffer, 128, 0, basicArray.size());
        buffer.finalizeFiltering();

        assertEquals(43, buffer.size());
        for (int i = 0; i < 43; i++) {
            assertEquals(buffer.data[i], i*3);
        }
    }

    @Test
    void reject() {
        int[] vals = new int[128];
        for (int i = 0; i < vals.length; i++) { vals[i] = i; }
        var buffer = new IntQueryBuffer(vals, 128);

        basicArray.reject(buffer, 128, 0, basicArray.size());
        buffer.finalizeFiltering();

        assertEquals(128-43, buffer.size());
        int j = 0;
        for (int i = 0; i < 43; i++) {
            if (++j % 3 == 0) j++;
            assertEquals(buffer.data[i], j);
        }
    }
}