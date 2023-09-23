package nu.marginalia.array.algo;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.page.LongArrayPage;
import nu.marginalia.array.page.PagingLongArray;
import nu.marginalia.array.scheme.PowerOf2PartitioningScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LongArrayTransformations2Test {
    LongArray basic;
    LongArray paged;
    LongArray shifted;
    LongArray segment;

    final int size = 1026;

    @BeforeEach
    public void setUp() {
        basic = LongArrayPage.onHeap(size);
        paged = PagingLongArray.newOnHeap(new PowerOf2PartitioningScheme(32), size);
        shifted = LongArrayPage.onHeap(size + 30).shifted(30);
        segment = LongArrayFactory.onHeapShared(size);

        long[] vals = new long[size];
        for (int i = 0; i < vals.length; i++) {
            vals[i] = i+10;
        }
        basic.set(0, vals);
        paged.set(0, vals);
        shifted.set(0, vals);
        segment.set(0, vals);
    }
    @Test
    void forEach() {
        foreachTester(basic);
        foreachTester(paged);
        foreachTester(shifted);
        foreachTester(segment);
    }
    @Test
    void transformEach() {
        transformTester(basic);
        transformTester(paged);
        transformTester(shifted);
        transformTester(segment);
    }

    @Test
    void transformEachIO() throws IOException {
        transformTesterIO(basic);
        transformTesterIO(paged);
        transformTesterIO(shifted);
        transformTesterIO(segment);
    }

    private void transformTester(LongArray array) {
        ArrayList<Long> offsets = new ArrayList<>();

        array.transformEach(0, size, (i, val) -> {
            assertEquals(i+10, val);
            offsets.add(i);
            return -val;
        });
        for (int i = 0; i < size; i++) {
            assertEquals(-(i+10), array.get(i));
        }
    }

    private void transformTesterIO(LongArray array) throws IOException {
        ArrayList<Long> offsets = new ArrayList<>();

        array.transformEachIO(0, size, (i, val) -> {
            assertEquals(i+10, val);
            offsets.add(i);
            return -val;
        });

        for (int i = 0; i < size; i++) {
            assertEquals(-(i+10), array.get(i));
        }

        for (int i = 0; i < size; i++) {
            assertEquals(offsets.get(i), i);
        }
    }

    private void foreachTester(LongArray array) {
        ArrayList<Long> offsets = new ArrayList<>();
        array.forEach(0, size, (i, val) -> {
            assertEquals(i+10, val);
            offsets.add(i);
        });
        for (int i = 0; i < size; i++) {
            assertEquals(offsets.get(i), i);
        }
    }
}