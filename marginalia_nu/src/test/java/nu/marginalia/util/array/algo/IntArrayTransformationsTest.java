package nu.marginalia.util.array.algo;

import nu.marginalia.util.array.IntArray;
import nu.marginalia.util.array.page.IntArrayPage;
import nu.marginalia.util.array.page.PagingIntArray;
import nu.marginalia.util.array.scheme.PowerOf2PartitioningScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntArrayTransformationsTest {
    IntArray basic;
    IntArray paged;
    IntArray shifted;

    final int size = 1026;

    @BeforeEach
    public void setUp() {
        basic = IntArrayPage.onHeap(size);
        paged = PagingIntArray.newOnHeap(new PowerOf2PartitioningScheme(32), size);
        shifted = IntArrayPage.onHeap(size + 30).shifted(30);

        for (int i = 0; i < basic.size(); i++) {
            basic.set(i, 3*i);
            paged.set(i, 3*i);
            shifted.set(i, 3*i);
        }
    }

    @Test
    void transformEach() {
        transformTester(basic);
        transformTester(paged);
        transformTester(shifted);
    }

    @Test
    void transformEachIO() throws IOException {
        transformTesterIO(basic);
        transformTesterIO(paged);
        transformTesterIO(shifted);
    }

    @Test
    void foldIO() throws IOException {
        assertEquals(3*(5+6+7+8+9), basic.foldIO(0, 5, 10, Integer::sum));
        assertEquals(3*(5+6+7+8+9), paged.foldIO(0, 5, 10, Integer::sum));
        assertEquals(3*(5+6+7+8+9), shifted.foldIO(0, 5, 10, Integer::sum));
    }

    private void transformTester(IntArray array) {
        array.transformEach(5, 15, (i, o) -> (int) (i - o));
        for (int i = 0; i < 5; i++) {
            assertEquals(3*i, array.get(i));
        }
        for (int i = 5; i < 15; i++) {
            assertEquals(-2*i, array.get(i));
        }
        for (int i = 15; i < 20; i++) {
            assertEquals(3*i, array.get(i));
        }
    }

    private void transformTesterIO(IntArray array) throws IOException {
        array.transformEachIO(5, 15, (i, o) -> (int) (i - o));
        for (int i = 0; i < 5; i++) {
            assertEquals(3*i, array.get(i));
        }
        for (int i = 5; i < 15; i++) {
            assertEquals(-2*i, array.get(i));
        }
        for (int i = 15; i < 20; i++) {
            assertEquals(3*i, array.get(i));
        }
    }
}