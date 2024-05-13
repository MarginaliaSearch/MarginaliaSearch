package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.buffer.LongQueryBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongArraySearchTest {

    LongArray basicArray = LongArray.allocate(1024);
    LongArray segmentArray = LongArrayFactory.onHeapConfined(1024);

    LongArray shiftedArray = LongArray.allocate(1054).range(30, 1054);

    @BeforeEach
    public void setUp() {
        for (int i = 0; i < basicArray.size(); i++) {
            basicArray.set(i, 3L*i);
            shiftedArray.set(i, 3L*i);
            segmentArray.set(i, 3L*i);
        }
    }

    @Test
    void linearSearch() {
        linearSearchTester(basicArray);
        linearSearchTester(shiftedArray);
        linearSearchTester(segmentArray);
    }

    @Test
    void binarySearch() {
        binarySearchTester(basicArray);
        binarySearchTester(shiftedArray);
        binarySearchTester(segmentArray);
    }

    @Test
    void binarySearchUpperBound() {
        binarySearchUpperBoundTester(basicArray);
        binarySearchUpperBoundTester(shiftedArray);
        binarySearchUpperBoundTester(segmentArray);
    }

    @Test
    void binarySearchUpperBoundNative() {
        binarySearchUpperBoundNativeTester(basicArray);
        binarySearchUpperBoundNativeTester(shiftedArray);
        binarySearchUpperBoundNativeTester(segmentArray);
    }


    @Test
    public void testEmptyRange() {
        assertTrue(segmentArray.binarySearchN(2, 0, 0, 0) < 0);
        assertTrue(segmentArray.linearSearchN(2, 0, 0, 0) < 0);
        assertTrue(segmentArray.binarySearch(0, 0, 0) < 0);
        assertTrue(segmentArray.linearSearch(0, 0, 0) < 0);
    }

    void linearSearchTester(LongArray array) {
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

    void binarySearchTester(LongArray array) {
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

    void binarySearchUpperBoundTester(LongArray array) {
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

    void binarySearchUpperBoundNativeTester(LongArray array) {
        for (int i = 0; i < array.size() * 3; i++) {
            long ret = array.binarySearchNativeUB(i, 0, array.size());

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
        long[] vals = new long[128];
        for (int i = 0; i < vals.length; i++) { vals[i] = i; }
        var buffer = new LongQueryBuffer(vals, 128);

        basicArray.retain(buffer, 128, 0, basicArray.size());
        buffer.finalizeFiltering();

        assertEquals(43, buffer.size());
        for (int i = 0; i < 43; i++) {
            assertEquals(buffer.data.get(i), i*3);
        }
    }

    @Test
    void reject() {
        long[] vals = new long[128];
        for (int i = 0; i < vals.length; i++) { vals[i] = i; }
        var buffer = new LongQueryBuffer(vals, 128);

        basicArray.reject(buffer, 128, 0, basicArray.size());
        buffer.finalizeFiltering();

        assertEquals(128-43, buffer.size());
        int j = 0;
        for (int i = 0; i < 43; i++) {
            if (++j % 3 == 0) j++;
            assertEquals(buffer.data.get(i), j);
        }
    }
}