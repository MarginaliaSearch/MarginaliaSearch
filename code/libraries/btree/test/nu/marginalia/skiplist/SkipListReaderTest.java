package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.LongStream;

public class SkipListReaderTest {
    Path outputFile;

    @BeforeEach
    void setUp() throws IOException {
        outputFile = Files.createTempFile(SkipListWriterTest.class.getSimpleName(), ".output.dat");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(outputFile);
    }

    LongArray createArray(long[] keys, long[] values) {
        assert keys.length == values.length;
        MemorySegment ms = Arena.ofAuto().allocate(keys.length * 16);
        for (int i = 0; i < keys.length; i++) {
            ms.setAtIndex(ValueLayout.JAVA_LONG, 2L*i, keys[i]);
            ms.setAtIndex(ValueLayout.JAVA_LONG, 2L*i+1, values[i]);
        }
        return LongArrayFactory.wrap(ms);
    }

    @Test
    public void testTenBlocks() throws IOException {
        long[] keys = LongStream.range(0, 300).toArray();
        long[] vals = LongStream.range(0, 300).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(outputFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);
            while (!reader.atEnd()) {
                System.out.println(reader.estimateSize());
                System.out.println(reader.getData(lqb));
                System.out.println(Arrays.toString(lqb.copyData()));
                lqb.zero();
            }
        }

        System.out.println("---");

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(40);
            while (!reader.atEnd()) {
                System.out.println(reader.estimateSize());
                System.out.println(reader.getData(lqb));
                System.out.println(Arrays.toString(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }
    }

    @Test
    public void testRetainTenBlocks() throws IOException {
        long[] keys = LongStream.range(0, 300).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 300).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(outputFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 4, 5, 30, 39, 270, 300, 551 }, 7);
            reader.retainData(lqb);
            lqb.finalizeFiltering();
            System.out.println(Arrays.toString(lqb.copyData()));
        }
    }

    @Test
    public void testGetValues() throws IOException {
        long[] keys = LongStream.range(0, 300).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 300).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(outputFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            long[] queryKeys = new long[] { 4, 5, 30, 39, 270, 300, 551 };
            long[] queryVals = reader.getValues(queryKeys);
            System.out.println(Arrays.toString(queryVals));
        }
    }

    @Test
    public void getData2() throws IOException {
        long[] keys = new long[] { 100,101 };
        long[] vals = new long[] { 50,51 };

        long pos = 0;
        try (var writer = new SkipListWriter(outputFile)) {
            pos = writer.writeList(createArray(keys, vals), 0, keys.length);
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, pos);
            LongQueryBuffer lqb = new LongQueryBuffer(4);
            reader.getData(lqb);
            System.out.println(Arrays.toString(lqb.copyData()));
        }
    }

    @Test
    public void testWtf() {
        LongArrayList vals = new LongArrayList();
        for (int i = 0; i < 255; i++) {
            vals.add(i);
            vals.add(-i);
        }

        try (LongArray array = LongArrayFactory.onHeapConfined(4096);
             var writer = new SkipListWriter(outputFile)) {
            writer.pad(4104);
            for (int i = 0; i < vals.size(); i++) {
                array.set(i, vals.getLong(i));
            }

            long pos = writer.writeList(array, 513, 255*2);

            System.out.println(pos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 4104);
            long[] queryKeys = new long[] { 100 };
            var lqb = new LongQueryBuffer(32);
            reader.getData(lqb);
            System.out.println(Arrays.toString(lqb.copyData()));


        }
    }


    @Test
    public void testAlignment1to64() throws IOException {
        for (int alignment = 0; alignment < 64; alignment++) {
            System.out.println("Testing alignment: " + alignment);

            long[] keys = new long[] { 1 };
            long[] vals = new long[] { 2 };

            long pos;
            try (var writer = new SkipListWriter(outputFile)) {
                writer.pad(8 * alignment);
                pos = writer.writeList(createArray(keys, vals), 0, keys.length);
            }

            try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
                var reader = new SkipListReader(pool, pos);
                var lqb = new LongQueryBuffer(32);
                reader.getData(lqb);
                Assertions.assertArrayEquals(new long[] { 1 }, lqb.copyData());

                reader.reset();

                long[] gotVals = reader.getValues(keys);
                Assertions.assertArrayEquals(gotVals, vals);
            }
        }
    }

    @Test
    public void testAlignment62() throws IOException {
        int alignment = 62;
        System.out.println("Testing alignment: " + alignment);

        long[] keys = new long[] { 1 };
        long[] vals = new long[] { 2 };

        long pos;
        try (var writer = new SkipListWriter(outputFile)) {
            writer.pad(8 * alignment);
            pos = writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, pos);
            var lqb = new LongQueryBuffer(32);
            reader.getData(lqb);
            System.out.println(Arrays.toString(lqb.copyData()));

            reader.reset();

            long[] gotVals = reader.getValues(keys);
            Assertions.assertArrayEquals(gotVals, vals);
        }
    }

    @Test
    public void testGetValues1() throws IOException {
        long[] keys = new long[] { 100 };
        long[] vals = new long[] { 50 };

        long pos = 0;
        try (var writer = new SkipListWriter(outputFile)) {
            pos = writer.writeList(createArray(keys, vals), 0, keys.length);
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, pos);
            long[] queryKeys = new long[] { 100 };
            long[] queryVals = reader.getValues(queryKeys);
            System.out.println(Arrays.toString(queryVals));
        }
    }


    @Test
    public void testRejectTenBlocks() throws IOException {
        long[] keys = LongStream.range(0, 300).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 300).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(outputFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(outputFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 4, 5, 30, 39, 270, 300, 551 }, 7);
            reader.rejectData(lqb);
            System.out.println(Arrays.toString(lqb.copyData()));
        }
    }

}
