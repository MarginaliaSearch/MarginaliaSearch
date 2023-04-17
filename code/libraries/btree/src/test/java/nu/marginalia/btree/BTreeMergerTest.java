package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.algo.TwoArrayOperations;
import nu.marginalia.array.delegate.ShiftedLongArray;
import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.LongUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class BTreeMergerTest {

    @Test
    void merge1Vanilla() throws IOException {
        BTreeContext ctx = new BTreeContext(4, 1, BTreeBlockSize.BS_64);

        LongArray a = LongArray.allocate(ctx.calculateSize(1024));
        LongArray b = LongArray.allocate(ctx.calculateSize(512));

        new BTreeWriter(a, ctx).write(0, 1024, generate(i -> 4*i));
        new BTreeWriter(b, ctx).write(0, 512, generate(i -> 3*i));

        var aReader = new BTreeReader(a, ctx, 0);
        var bReader = new BTreeReader(b, ctx, 0);
        long cSize = ctx.calculateSize(1024 + 512);
        LongArray c = LongArray.allocate(cSize);

        long written = BTreeMerger.merge1(aReader, bReader, new BTreeWriter(c, ctx), 0);

        assertTrue(cSize >= written);

        BTreeReader cReader = new BTreeReader(c, ctx, 0);

        // Check that the number of entries is correct
        assertEquals(cReader.numEntries(), TwoArrayOperations.countDistinctElements(
                aReader.data(), bReader.data(),
                0, aReader.numEntries(),
                0, bReader.numEntries()));

        // Check that all values are present
        for (int i = 0; i < 1024*5; i++) {
            boolean expectTrue = false;
            if (i / 4 < 1024 && i % 4 == 0) {
                expectTrue = true;
            }
            if (i / 3 < 512 && i % 3 == 0) {
                expectTrue = true;
            }

            assertEquals(expectTrue, cReader.findEntry(i) >= 0);
        }
    }


    @Test
    void merge1OneEmpty() throws IOException {
        BTreeContext ctx = new BTreeContext(4, 1, BTreeBlockSize.BS_64);

        LongArray a = LongArray.allocate(ctx.calculateSize(1024));
        LongArray b = LongArray.allocate(ctx.calculateSize(10));

        new BTreeWriter(a, ctx).write(0, 1024, generate((i -> 4*i)));
        new BTreeWriter(b, ctx).write(0, 0, generate((i -> 3*i)));

        var aReader = new BTreeReader(a, ctx, 0);
        var bReader = new BTreeReader(b, ctx, 0);
        long cSize = ctx.calculateSize(1024 + 512);
        LongArray c = LongArray.allocate(cSize);

        long written = BTreeMerger.merge1(aReader, bReader, new BTreeWriter(c, ctx), 0);

        assertTrue(cSize >= written);

        BTreeReader cReader = new BTreeReader(c, ctx, 0);

        // Check that the number of entries is correct
        assertEquals(cReader.numEntries(), TwoArrayOperations.countDistinctElements(
                aReader.data(), bReader.data(),
                0, aReader.numEntries(),
                0, bReader.numEntries()));

        // Check that all values are present
        for (int i = 0; i < 1024*5; i++) {
            boolean expectTrue = false;
            if (i / 4 < 1024 && i % 4 == 0) {
                expectTrue = true;
            }

            assertEquals(expectTrue, cReader.findEntry(i) >= 0);
        }
    }

    @Test
    void merge2Vanilla() throws IOException {
        BTreeContext ctx = new BTreeContext(4, 2, BTreeBlockSize.BS_64);

        LongArray a = LongArray.allocate(ctx.calculateSize(1024));
        LongArray b = LongArray.allocate(ctx.calculateSize(512));

        new BTreeWriter(a, ctx).write(0, 512, generate(i -> i, i -> 2*i));
        new BTreeWriter(b, ctx).write(0, 256, generate(i -> 2*i, i -> 6*i));

        long cSize = ctx.calculateSize(1024 + 512);
        var aReader = new BTreeReader(a, ctx, 0);
        var bReader = new BTreeReader(b, ctx, 0);

        LongArray c = LongArray.allocate(cSize);
        BTreeMerger.merge2(aReader, bReader, new BTreeWriter(c, ctx), Long::sum, 0);

        BTreeReader cReader = new BTreeReader(c, ctx, 0);

        for (int i = 0; i < 512; i++) {
            long offset = cReader.findEntry(i);
            assertTrue(offset >= 0);

            long data = cReader.data().get(offset + 1);

            if (i % 2 == 0) {
                assertEquals(5*i, data);
            } else {
                assertEquals(2*i, data);
            }
        }
    }

    @Test
    void merge2LeftEmpty() throws IOException {
        BTreeContext ctx = new BTreeContext(4, 2, BTreeBlockSize.BS_64);

        LongArray a = LongArray.allocate(ctx.calculateSize(0));
        LongArray b = LongArray.allocate(ctx.calculateSize(512));

        new BTreeWriter(a, ctx).write(0, 0, generate(i -> i, i -> 2*i));
        new BTreeWriter(b, ctx).write(0, 256, generate(i -> 2*i, i -> 6*i));

        long cSize = ctx.calculateSize(256);
        var aReader = new BTreeReader(a, ctx, 0);
        var bReader = new BTreeReader(b, ctx, 0);

        LongArray c = LongArray.allocate(cSize);
        long mergedSize = BTreeMerger.merge2(aReader, bReader, new BTreeWriter(c, ctx), Long::sum, 0);
        assertEquals(cSize, mergedSize);

        BTreeReader cReader = new BTreeReader(c, ctx, 0);
        System.out.println(Arrays.toString(((ShiftedLongArray) cReader.data()).toArray()));
        for (int i = 0; i < 256; i++) {
            long offset = cReader.findEntry(2 * i);
            assertTrue(offset >= 0);

            long data = cReader.data().get(offset + 1);

            assertEquals(6*i, data);
        }
    }


    @Test
    void merge2RightEmpty() throws IOException {
        BTreeContext ctx = new BTreeContext(4, 2, BTreeBlockSize.BS_64);

        LongArray a = LongArray.allocate(ctx.calculateSize(0));
        LongArray b = LongArray.allocate(ctx.calculateSize(512));

        new BTreeWriter(a, ctx).write(0, 0, generate(i -> i, i -> 2*i));

        new BTreeWriter(b, ctx).write(0, 256, generate(i -> 2*i, i -> 6*i));

        long cSize = ctx.calculateSize(256);
        var aReader = new BTreeReader(a, ctx, 0);
        var bReader = new BTreeReader(b, ctx, 0);

        LongArray c = LongArray.allocate(cSize);


        //                                   v-- swapped --v
        long mergedSize = BTreeMerger.merge2(bReader, aReader, new BTreeWriter(c, ctx), Long::sum, 0);
        assertEquals(cSize, mergedSize);

        BTreeReader cReader = new BTreeReader(c, ctx, 0);
        for (int i = 0; i < 256; i++) {
            long offset = cReader.findEntry(2 * i);
            assertTrue(offset >= 0);

            long data = cReader.data().get(offset + 1);
            assertEquals(6*i, data);
        }
    }


    /**
     * Generate a BTree callback that will populate the slice with the values generated by the given generator.
     */
    BTreeWriteCallback generate(LongUnaryOperator generator) {
        return slice -> slice.transformEach(0, slice.size(), (i, v) -> generator.applyAsLong(i));
    }

    /**
     * Generate a BTree callback that will populate the slice with the keys and values generated by the given generators.
     */
    BTreeWriteCallback generate(LongUnaryOperator keyGen, LongUnaryOperator valGen) {
        return slice -> {
            for (int i = 0; i < slice.size(); i+=2) {
                slice.set(i, keyGen.applyAsLong(i/2));
                slice.set(i+1, valGen.applyAsLong(i/2));
            }
        };
    }

}