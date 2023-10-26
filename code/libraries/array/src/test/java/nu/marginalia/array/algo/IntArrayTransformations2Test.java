package nu.marginalia.array.algo;

import nu.marginalia.array.IntArray;
import nu.marginalia.array.scheme.PowerOf2PartitioningScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntArrayTransformations2Test {
    IntArray basic;
    IntArray paged;
    IntArray shifted;

    final int size = 1026;

    @BeforeEach
    public void setUp() {
        basic = IntArray.allocate(size);
        paged = IntArray.allocate(size);
        shifted = IntArray.allocate(size+30).shifted(30);

        int[] vals = new int[size];
        for (int i = 0; i < vals.length; i++) {
            vals[i] = i+10;
        }
        basic.set(0, vals);
        paged.set(0, vals);
        shifted.set(0, vals);
    }
    @Test
    void forEach() {
        foreachTester(basic);
        foreachTester(paged);
        foreachTester(shifted);
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

    private void transformTester(IntArray array) {
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

    private void transformTesterIO(IntArray array) throws IOException {
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

    private void foreachTester(IntArray array) {
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