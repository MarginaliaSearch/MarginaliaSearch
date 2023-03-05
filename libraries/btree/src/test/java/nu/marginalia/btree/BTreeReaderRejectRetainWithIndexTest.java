package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.util.PrimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class BTreeReaderRejectRetainWithIndexTest {
    BTreeContext ctx = new BTreeContext(5, 1, BTreeBlockSize.BS_32);
    LongArray array;

    @BeforeEach
    public void setUp() throws IOException {
        array = LongArray.allocate(65536);
        new BTreeWriter(array, ctx).write(0, 1000, slice -> {
            int p = 2;
            for (int idx = 0; idx < 1000; idx++) {
                slice.set(idx, p);
                p = (int) PrimeUtil.nextPrime(p + 1, 1);
            }
        });
    }

    @Test
    public void testRetain() {
        LongQueryBuffer odds = new LongQueryBuffer(50);
        Arrays.setAll(odds.data, i -> 2L*i + 1);

        BTreeReader reader = new BTreeReader(array, ctx, 0);
        reader.retainEntries(odds);
        odds.finalizeFiltering();

        long[] primeOdds = odds.copyData();
        long[] first100OddPrimes = new long[] { 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97 };
        assertArrayEquals(first100OddPrimes, primeOdds);
    }

    @Test
    public void testReject() {
        LongQueryBuffer odds = new LongQueryBuffer(50);
        Arrays.setAll(odds.data, i -> 2L*i + 1);

        BTreeReader reader = new BTreeReader(array, ctx, 0);
        reader.rejectEntries(odds);
        odds.finalizeFiltering();

        long[] nonPrimeOdds = odds.copyData();
        long[] first100OddNonPrimes = new long[] { 1, 9, 15, 21, 25, 27, 33, 35, 39, 45, 49, 51, 55, 57, 63, 65, 69, 75, 77, 81, 85, 87, 91, 93, 95, 99 };
        assertArrayEquals(first100OddNonPrimes, nonPrimeOdds);
    }
}
