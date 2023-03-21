package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.page.LongArrayPage;
import nu.marginalia.array.page.PagingLongArray;
import nu.marginalia.array.scheme.PowerOf2PartitioningScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LongArrayTransformationsTest {
    LongArray basic;
    LongArray paged;
    LongArray shifted;

    final int size = 1026;

    @BeforeEach
    public void setUp() {
        basic = LongArrayPage.onHeap(size);
        paged = PagingLongArray.newOnHeap(new PowerOf2PartitioningScheme(32), size);
        shifted = LongArrayPage.onHeap(size + 30).shifted(30);

        for (int i = 0; i < basic.size(); i++) {
            basic.set(i, 3L*i);
            paged.set(i, 3L*i);
            shifted.set(i, 3L*i);
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
    void fold() {
        assertEquals(3*(5+6+7+8+9), basic.fold(0, 5, 10, Long::sum));
        assertEquals(3*(5+6+7+8+9), paged.fold(0, 5, 10, Long::sum));
        assertEquals(3*(5+6+7+8+9), shifted.fold(0, 5, 10, Long::sum));
    }

    @Test
    void foldIO() throws IOException {
        assertEquals(3*(5+6+7+8+9), basic.foldIO(0, 5, 10, Long::sum));
        assertEquals(3*(5+6+7+8+9), paged.foldIO(0, 5, 10, Long::sum));
        assertEquals(3*(5+6+7+8+9), shifted.foldIO(0, 5, 10, Long::sum));
    }

    private void transformTester(LongArray array) {
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

    private void transformTesterIO(LongArray array) throws IOException {
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