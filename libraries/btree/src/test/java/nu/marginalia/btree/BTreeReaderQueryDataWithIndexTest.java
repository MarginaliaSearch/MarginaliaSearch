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
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BTreeReaderQueryDataWithIndexTest {
    BTreeContext ctx = new BTreeContext(5, 2, BTreeBlockSize.BS_32);
    LongArray array;

    @BeforeEach
    public void setUp() throws IOException {
        array = LongArray.allocate(65536);
        new BTreeWriter(array, ctx).write(0, 1000, slice -> {
            for (int idx = 0; idx < 1000; idx++) {
                slice.set(idx * 2,       2 * idx);
                slice.set(idx * 2 + 1,   5 * idx);
            }
        });

        // we expect index[key] = 5 * key / 2;
    }

    @Test
    public void testQueryData() {
        long[] keys = new long[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        BTreeReader reader = new BTreeReader(array, ctx, 0);

        long[] data = reader.queryData(keys, 1);

        assertArrayEquals(data, new long[] { 0, 5, 0, 10, 0, 15, 0, 20, 0, 25 });
    }

}
