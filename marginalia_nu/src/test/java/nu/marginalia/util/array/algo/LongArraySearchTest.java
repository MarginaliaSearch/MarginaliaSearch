package nu.marginalia.util.array.algo;

import nu.marginalia.util.array.LongArray;
import nu.marginalia.util.array.buffer.LongQueryBuffer;
import nu.marginalia.util.array.page.PagingLongArray;
import nu.marginalia.util.array.scheme.PowerOf2PartitioningScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongArraySearchTest {

    LongArray basicArray = LongArray.allocate(1024);
    LongArray pagingArray = PagingLongArray.newOnHeap(new PowerOf2PartitioningScheme(64), 1024);

    LongArray shiftedArray = LongArray.allocate(1054).range(30, 1054);

    @BeforeEach
    public void setUp() {
        for (int i = 0; i < basicArray.size(); i++) {
            basicArray.set(i, 3L*i);
            pagingArray.set(i, 3L*i);
            shiftedArray.set(i, 3L*i);
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
    void binarySearchUpperBound() {
        binarySearchUpperBoundTester(basicArray);
        binarySearchUpperBoundTester(pagingArray);
        binarySearchUpperBoundTester(shiftedArray);
    }

    @Test
    void linearSearchUpperBound() {
        linearSearchUpperBoundTester(basicArray);
        linearSearchUpperBoundTester(pagingArray);
        linearSearchUpperBoundTester(shiftedArray);
    }

    void linearSearchTester(LongArray array) {
        for (int i = 0; i < array.size() * 3; i++) {
            long ret = array.linearSearch(i, 0, array.size());

            if ((i % 3) == 0) {
                assertTrue(ret >= 0);
                assertEquals(i, array.get(ret));
            }
            else {
                long higher = LongArraySearch.decodeSearchMiss(ret);
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
                long higher = LongArraySearch.decodeSearchMiss(ret);
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
    void linearSearchUpperBoundTester(LongArray array) {
        for (int i = 0; i < array.size() * 3; i++) {
            long ret = array.linearSearchUpperBound(i, 0, array.size());
            long ret2 = array.binarySearchUpperBound(i, 0, array.size());

            assertEquals(ret, ret2);

            if ((i % 3) == 0) {
                assertTrue(ret >= 0);
                assertEquals(i, array.get(ret));
            }
            else {
                if (i > 0 && ret > 0 && ret < array.size()) {
                    System.out.println(ret);
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
            assertEquals(buffer.data[i], i*3);
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
            assertEquals(buffer.data[i], j);
        }
    }
}