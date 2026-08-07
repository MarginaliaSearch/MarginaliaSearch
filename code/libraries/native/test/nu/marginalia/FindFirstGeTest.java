package nu.marginalia;

import nu.marginalia.ffi.NativeAlgos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The vectorised scan must agree with a plain one at every length, including
 *  the lengths that straddle a vector's worth of values. */
class FindFirstGeTest {

    @BeforeAll
    static void available() {
        Assumptions.assumeTrue(NativeAlgos.isAvailable, "native library unavailable");
    }

    private static int reference(int[] data, int n, int target) {
        for (int i = 0; i < n; i++) {
            if (data[i] >= target) return i;
        }
        return n;
    }

    @Test
    void matchesReferenceAcrossLengthsAndTargets() {
        Random r = new Random(1);

        for (int length = 0; length <= 40; length++) {
            int[] data = new int[length];
            int value = 0;
            for (int i = 0; i < length; i++) {
                value += 1 + r.nextInt(4);
                data[i] = value;
            }

            // Every target from below the first value to past the last one
            for (int target = 0; target <= value + 2; target++) {
                assertEquals(reference(data, length, target), NativeAlgos.findFirstGe(data, length, target),
                        "length " + length + " target " + target);
                assertEquals(reference(data, length, target), NativeAlgos.findFirstGeScalar(data, length, target),
                        "scalar, length " + length + " target " + target);
            }
        }
    }

    @Test
    void readsNoFurtherThanTheGivenLength() {
        int[] data = new int[64];
        for (int i = 0; i < 32; i++) {
            data[i] = i;
        }
        // Values past the length would satisfy the search if they were read
        for (int i = 32; i < 64; i++) {
            data[i] = 1000;
        }

        assertEquals(32, NativeAlgos.findFirstGe(data, 32, 500));
    }

    @Test
    void findsAValueAtTheStartOfALongList() {
        int[] data = new int[512];
        for (int i = 0; i < data.length; i++) {
            data[i] = 10 + i;
        }

        assertEquals(0, NativeAlgos.findFirstGe(data, data.length, 0));
        assertEquals(data.length - 1, NativeAlgos.findFirstGe(data, data.length, 10 + data.length - 1));
    }
}
