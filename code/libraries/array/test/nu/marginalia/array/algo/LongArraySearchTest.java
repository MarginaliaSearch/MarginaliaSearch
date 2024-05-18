package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.page.LongQueryBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongArraySearchTest {

    LongArray segmentArray = LongArrayFactory.onHeapConfined(1024);
    LongArray shiftedArray = LongArray.allocate(1054).range(30, 1054);

    @BeforeEach
    public void setUp() {
        for (int i = 0; i < shiftedArray.size(); i++) {
            shiftedArray.set(i, 3L*i);
            segmentArray.set(i, 3L*i);
        }
    }


    @Test
    void binarySearch() {
        binarySearchTester(shiftedArray);
        binarySearchTester(segmentArray);
    }

    @Test
    public void testEmptyRange() {
        assertTrue(segmentArray.binarySearchN(2, 0, 0, 0) <= 0);
        assertTrue(segmentArray.binarySearch(0, 0, 0) <= 0);
    }


    void binarySearchTester(LongArray array) {
        for (int i = 0; i < array.size() * 3; i++) {
            long ret = array.binarySearch(i, 0, array.size());

            assertTrue(ret >= 0);

            // Invariant check
            if (i > 0 && ret > 0 && ret + 1 < array.size()) {
                assertTrue(array.get(ret - 1) < i);
                assertTrue(array.get(ret) >= i);
                assertTrue(array.get(ret + 1) > i);
            }

            if ((i % 3) == 0) {
                assertEquals(i, array.get(ret));
            }
        }
    }

    @Test
    void retain() {
        long[] vals = new long[128];
        for (int i = 0; i < vals.length; i++) { vals[i] = i; }
        var buffer = new LongQueryBuffer(vals, 128);

        segmentArray.retain(buffer, 128, 0, segmentArray.size());
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

        segmentArray.reject(buffer, 128, 0, segmentArray.size());
        buffer.finalizeFiltering();

        assertEquals(128-43, buffer.size());
        int j = 0;
        for (int i = 0; i < 43; i++) {
            if (++j % 3 == 0) j++;
            assertEquals(buffer.data.get(i), j);
        }
    }
}