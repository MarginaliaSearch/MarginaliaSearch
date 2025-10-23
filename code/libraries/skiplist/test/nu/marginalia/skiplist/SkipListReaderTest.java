package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import java.util.Random;
import java.util.stream.LongStream;

import static nu.marginalia.skiplist.SkipListConstants.*;

public class SkipListReaderTest {
    static {
        System.setProperty("system.noSunMiscUnsafe", "TRUE");
    }

    Path docsFile;

    @BeforeEach
    void setUp() throws IOException {
        docsFile = Files.createTempFile(SkipListWriterTest.class.getSimpleName(), ".docs.dat");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(docsFile);
    }

    LongArray createArray(long[] keys, long[] values) {
        return createArray(Arena.ofAuto(), keys, values);
    }

    LongArray createArray(Arena arena, long[] keys, long[] values) {
        assert keys.length == values.length;
        MemorySegment ms = arena.allocate(keys.length * RECORD_SIZE*8);
        for (int i = 0; i < keys.length; i++) {
            ms.setAtIndex(ValueLayout.JAVA_LONG, RECORD_SIZE*i, keys[i]);
            for (int vi = 1; vi < RECORD_SIZE; vi++) {
                ms.setAtIndex(ValueLayout.JAVA_LONG, RECORD_SIZE * i + vi, values[i]);
            }
        }
        return LongArrayFactory.wrap(ms);
    }

    @Test
    public void testGetKeysWithRange__sunny_day() throws IOException {
        long[] keys = LongStream.range(0, 1000).toArray();
        long[] vals = LongStream.range(0, 1000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        LongSet actualKeys = new LongArraySet(keys.length);
        LongSet expectedKeys = new LongArraySet(LongList.of(
                0, 1, 2, 3, 4, 5,
                100, 101, 102, 103, 104, 105,
                995, 996, 997, 998, 999));


        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);

            SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 0, 100, 995 }, new long[] { 6, 106, 1000 });

            while (!reader.atEnd()) {
                reader.getKeys(lqb, ranges);
                actualKeys.addAll(LongList.of(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }

        Assertions.assertEquals(expectedKeys, actualKeys);

    }

    @Test
    public void testGetKeysWithRange__gaps() throws IOException {
        long[] keys = LongStream.range(0, 500).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 500).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        LongSet actualKeys = new LongArraySet(keys.length);
        LongSet expectedKeys = new LongArraySet(LongList.of(
                0, 2, 4,
                102, 104, 106,
                996, 998));


        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);

            SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 0, 101, 995 }, new long[] { 6, 107, 1000 });

            while (!reader.atEnd()) {
                reader.getKeys(lqb, ranges);
                actualKeys.addAll(LongList.of(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }

        Assertions.assertEquals(expectedKeys, actualKeys);

    }


    @Test
    public void testGetKeysWithRange__edges_1block() throws IOException {
        long[] keys = LongStream.range(0, 1000).toArray();
        long[] vals = LongStream.range(0, 1000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        LongSet actualKeys = new LongArraySet(keys.length);
        LongSet expectedKeys = new LongArraySet(LongList.of(
                0, 1,
                998, 999));


        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);

            SkipListValueRanges ranges = new SkipListValueRanges(new long[] { -2, 998 }, new long[] { 2, 1002 });

            while (!reader.atEnd()) {
                reader.getKeys(lqb, ranges);
                actualKeys.addAll(LongList.of(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }

        Assertions.assertEquals(expectedKeys, actualKeys);

    }

    @Test
    public void testGetKeysWithRange__edges_2block() throws IOException {
        long[] keys = LongStream.range(0, 32000).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        LongSet actualKeys = new LongArraySet(keys.length);
        LongSet expectedKeys = new LongArraySet();

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);

            int nValuesInFirstBlock = SkipListReader.parseBlocks(pool, 0).getFirst().n();
            SkipListValueRanges ranges = new SkipListValueRanges(new long[] { nValuesInFirstBlock-2 }, new long[] { nValuesInFirstBlock+2 });
            for (long i = ranges.start(); i < ranges.end(); i++) {
                expectedKeys.add(i);
            }

            while (!reader.atEnd()) {
                reader.getKeys(lqb, ranges);
                actualKeys.addAll(LongList.of(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }

        Assertions.assertEquals(expectedKeys, actualKeys);

    }


    @Test
    public void testGetKeysWithRange__find_block() throws IOException {
        long[] keys = LongStream.range(0, 32000).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        LongSet actualKeys = new LongArraySet(keys.length);
        LongSet expectedKeys = new LongArraySet();

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);

            var thirdBlock = SkipListReader.parseBlocks(pool, 0).get(2);
            var thirdBlockValues = thirdBlock.docIds();

            SkipListValueRanges ranges = new SkipListValueRanges(new long[] { thirdBlockValues.getLong(3) }, new long[] { thirdBlockValues.getLong(5) });
            for (long i = ranges.start(); i < ranges.end(); i++) {
                expectedKeys.add(i);
            }

            while (!reader.atEnd()) {
                reader.getKeys(lqb, ranges);
                actualKeys.addAll(LongList.of(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }

        Assertions.assertEquals(expectedKeys, actualKeys);

    }


    @Test
    public void testGetKeysWithRange__find_block__missing() throws IOException {
        long[] keys = LongStream.range(0, 32000).map(v -> v*5).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        LongSet actualKeys = new LongArraySet(keys.length);
        LongSet expectedKeys = new LongArraySet();

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);

            var thirdBlock = SkipListReader.parseBlocks(pool, 0).get(2);
            var thirdBlockValues = thirdBlock.docIds();

            SkipListValueRanges ranges = new SkipListValueRanges(new long[] { thirdBlockValues.getLong(3)+1 }, new long[] { thirdBlockValues.getLong(3)+2 });

            while (!reader.atEnd()) {
                reader.getKeys(lqb, ranges);
                actualKeys.addAll(LongList.of(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }

        Assertions.assertEquals(expectedKeys, actualKeys);

    }


    @Test
    public void testTenBlocks() throws IOException {
        long[] keys = LongStream.range(0, 32000).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        LongSet actualKeys = new LongArraySet(keys.length);
        LongSet expectedKeys = new LongArraySet(LongList.of(keys));

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(20);
            while (!reader.atEnd()) {
                reader.getKeys(lqb);
                actualKeys.addAll(LongList.of(lqb.copyData()));
                if (!lqb.fitsMore()) {
                    lqb.zero();
                }
            }
        }

        Assertions.assertEquals(expectedKeys, actualKeys);

    }

    @Test
    public void testRetainTenBlocks() throws IOException {
        long[] keys = LongStream.range(0, 32000).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }


        long[] requestKeys = new long[] { 4, 5, 30, 39, 270, 300, 551, 8000, 9981, 16600 };
        long[] expectedResult = new long[] { 4, 30, 270, 300, 8000, 16600 };

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(requestKeys, requestKeys.length);
            reader.retainData(lqb);
            lqb.finalizeFiltering();

            long[] actualResult = lqb.copyData();

            System.out.println(Arrays.toString(actualResult));
            Assertions.assertArrayEquals(expectedResult, actualResult);
        }
    }


    @Test
    public void testRetainBug() throws IOException {
        long[] keys = LongStream.range(0, 30000).map(v -> v).toArray();
        long[] vals = LongStream.range(0, 30000).map(v -> -v).toArray();

        long start;
        try (var writer = new SkipListWriter(docsFile)) {
            writer.padDocuments(512);
            start = writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, start);
            long[] values = LongStream.range(0, 4059).toArray();
            LongQueryBuffer lqb = new LongQueryBuffer(values, values.length);

            reader.retainData(lqb);
            lqb.finalizeFiltering();
            System.out.println(Arrays.toString(lqb.copyData()));

            values = LongStream.range(4060, 4070).toArray();
            lqb = new LongQueryBuffer(values, values.length);
            reader.retainData(lqb);
            lqb.finalizeFiltering();
            System.out.println(Arrays.toString(lqb.copyData()));

        }
    }

    @Test
    public void testGetAllValues__compactBlock() throws IOException {
        long[] keys = LongStream.range(0, 300).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 300).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            long[] queryKeys = new long[] { 4, 5, 30, 39, 270, 300, 551 };
            long[] queryVals = reader.getAllValues(queryKeys);
            long[] expectedVals = new long[] { -4, 0, -30, 0, -270, -300, 0,
                                               -4, 0, -30, 0, -270, -300, 0 };

            System.out.println(Arrays.toString(expectedVals));
            System.out.println(Arrays.toString(queryVals));

            Assertions.assertArrayEquals(expectedVals, queryVals);
        }
    }

    @Test
    public void testGetAllValues__largeBlock__one_value_block() throws IOException {
        long[] keys = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK/2).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK/2).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);

            try (var page = pool.get(0)) {
                var block = SkipListReader.parseBlock(page.getMemorySegment(), 0);
                Assertions.assertEquals(FLAG_END_BLOCK, block.flags() & FLAG_END_BLOCK, "Expected a terminal block");
                Assertions.assertNotEquals(FLAG_COMPACT_BLOCK, block.flags() & FLAG_COMPACT_BLOCK, "Expected a non-compact block");
            }

            long[] queryKeys = new long[] { 4, 5, 30, 39, 270, 300, 551 };
            long[] queryVals = reader.getAllValues(queryKeys);
            long[] expectedVals = new long[] { -4, 0, -30, 0, -270, -300, 0,
                    -4, 0, -30, 0, -270, -300, 0 };

            System.out.println(Arrays.toString(expectedVals));
            System.out.println(Arrays.toString(queryVals));

            Assertions.assertArrayEquals(expectedVals, queryVals);
        }
    }

    @Test
    public void testGetAllValues__largeBlock__two_value_blocks() throws IOException {
        long[] keys = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);

            try (var page = pool.get(0)) {
                var block = SkipListReader.parseBlock(page.getMemorySegment(), 0);
                Assertions.assertEquals(FLAG_END_BLOCK, block.flags() & FLAG_END_BLOCK, "Expected a terminal block");
                Assertions.assertNotEquals(FLAG_COMPACT_BLOCK, block.flags() & FLAG_COMPACT_BLOCK, "Expected a non-compact block");
            }

            long[] queryKeys = new long[] { 4, 5, 30, 39, 270, 300, 551, 2*SkipListConstants.MAX_RECORDS_PER_BLOCK-2 };
            long[] queryVals = reader.getAllValues(queryKeys);
            long[] expectedVals = new long[] { -4, 0, -30, 0, -270, -300, 0, -2*SkipListConstants.MAX_RECORDS_PER_BLOCK+2,
                    -4, 0, -30, 0, -270, -300, 0, -2*SkipListConstants.MAX_RECORDS_PER_BLOCK+2 };

            System.out.println(Arrays.toString(expectedVals));
            System.out.println(Arrays.toString(queryVals));

            Assertions.assertArrayEquals(expectedVals, queryVals);
            Assertions.assertEquals(2, reader.__stats__valueReads);

        }
    }


    @Test
    public void testGetAllValues__largeBlock__two_value_blocks__just_block_1() throws IOException {
        int n = SkipListConstants.MAX_RECORDS_PER_BLOCK;

        long[] keys = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);

            try (var page = pool.get(0)) {
                var block = SkipListReader.parseBlock(page.getMemorySegment(), 0);
                Assertions.assertEquals(FLAG_END_BLOCK, block.flags() & FLAG_END_BLOCK, "Expected a terminal block");
                Assertions.assertNotEquals(FLAG_COMPACT_BLOCK, block.flags() & FLAG_COMPACT_BLOCK, "Expected a non-compact block");
            }

            long[] queryKeys = new long[] { 32 };
            long[] queryVals = reader.getAllValues(queryKeys);
            long[] expectedVals = new long[] { -32, -32 };

            System.out.println(Arrays.toString(expectedVals));
            System.out.println(Arrays.toString(queryVals));

            Assertions.assertArrayEquals(expectedVals, queryVals);
            Assertions.assertEquals(1, reader.__stats__valueReads);
        }
    }

    @Test
    public void testGetAllValues__largeBlock__two_value_blocks__just_block_2() throws IOException {
        int n = SkipListConstants.MAX_RECORDS_PER_BLOCK;
        int lastValue = n*2 - 4;

        long[] keys = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, SkipListConstants.MAX_RECORDS_PER_BLOCK).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);

            try (var page = pool.get(0)) {
                var block = SkipListReader.parseBlock(page.getMemorySegment(), 0);
                Assertions.assertEquals(FLAG_END_BLOCK, block.flags() & FLAG_END_BLOCK, "Expected a terminal block");
                Assertions.assertNotEquals(FLAG_COMPACT_BLOCK, block.flags() & FLAG_COMPACT_BLOCK, "Expected a non-compact block");
            }

            long[] queryKeys = new long[] { lastValue };
            long[] queryVals = reader.getAllValues(queryKeys);
            long[] expectedVals = new long[] { -lastValue, -lastValue };

            System.out.println(Arrays.toString(expectedVals));
            System.out.println(Arrays.toString(queryVals));

            Assertions.assertArrayEquals(expectedVals, queryVals);

            Assertions.assertEquals(1, reader.__stats__valueReads);
        }
    }

    @Test
    public void testGetAllValues__largeBlock__fuzz_case_1() throws IOException {

        long[] keys = LongStream.range(0, 32000).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        Random r = new Random();

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);

            SkipListReader.parseBlocks(pool, 0).forEach(System.out::println);

            long[] queryKeys = new long[]{2504, 55152};
            Arrays.sort(queryKeys);
            long[] expectedVals = new long[]{-2504, -55152, -2504, -55152};

            long[] queryVals = reader.getAllValues(queryKeys);

            System.out.println(Arrays.toString(expectedVals));
            System.out.println(Arrays.toString(queryVals));

            Assertions.assertArrayEquals(expectedVals, queryVals);
        }
    }


    @Test
    public void testGetAllValues__largeBlock__fuzz_23970_5180308326906() throws IOException {
        long[] keys = LongStream.range(0, 64971).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 64971).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);

            long[] queryKeys = new long[]{ 21312, 29194, 42856, 47940 };
            long[] expectedVals = new long[]{ -21312, -29194, -42856, -47940,
                                              -21312, -29194, -42856, -47940};

            long[] queryVals = reader.getAllValues(queryKeys);

            System.out.println(Arrays.toString(expectedVals));
            System.out.println(Arrays.toString(queryVals));

            Assertions.assertArrayEquals(expectedVals, queryVals);

            reader.reset();
        }
    }

    @Test
    public void getKeys2() throws IOException {
        long[] keys = new long[] { 100,101 };
        long[] vals = new long[] { 50,51 };

        long pos = 0;
        try (var writer = new SkipListWriter(docsFile)) {
            pos = writer.writeList(createArray(keys, vals), 0, keys.length);
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, pos);
            LongQueryBuffer lqb = new LongQueryBuffer(4);
            reader.getKeys(lqb);
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
             var writer = new SkipListWriter(docsFile)) {
            writer.padDocuments(4104);
            for (int i = 0; i < vals.size(); i++) {
                array.set(i, vals.getLong(i));
            }

            long pos = writer.writeList(array, 513, 255*2);

            System.out.println(pos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 4104);
            long[] queryKeys = new long[] { 100 };
            var lqb = new LongQueryBuffer(32);
            reader.getKeys(lqb);
            System.out.println(Arrays.toString(lqb.copyData()));


        }
    }

    @Test
    public void testRejectTenBlocks() throws IOException {
        long[] keys = LongStream.range(0, 32000).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -v).toArray();

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }


        long[] requestKeys = new long[] { 4, 5, 30, 39, 270, 300, 551, 8000, 9981, 16600 };
        long[] expectedResult = new long[] { 5, 39, 551, 9981 };

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            LongQueryBuffer lqb = new LongQueryBuffer(requestKeys, requestKeys.length);
            reader.rejectData(lqb);
            lqb.finalizeFiltering();

            long[] actualResult = lqb.copyData();

            System.out.println(Arrays.toString(actualResult));
            Assertions.assertArrayEquals(expectedResult, actualResult);
        }
    }

    @Test
    void retainInPage() {
        long[] keys = new long[] { 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 52, 54, 56, 58, 60, 62, 64, 66, 68, 70, 72, 74, 76, 78, 80, 82, 84, 86, 88, 90, 92, 94, 96, 98, 67108964, 67108966, 67108968, 67108970, 67108972, 67108974, 67108976, 67108978, 67108980, 67108982, 67108984, 67108986, 67108988, 67108990, 67108992, 67108994, 67108996, 67108998, 67109000, 67109002, 67109004, 67109006, 67109008, 67109010, 67109012, 67109014, 67109016, 67109018, 67109020, 67109022, 67109024, 67109026, 67109028, 67109030, 67109032, 67109034, 67109036, 67109038, 67109040, 67109042, 67109044, 67109046, 67109048, 67109050, 67109052, 67109054, 67109056, 67109058, 67109060, 67109062, 134217928, 134217930, 134217932, 134217934, 134217936, 134217938, 134217940, 134217942, 134217944, 134217946, 134217948, 134217950, 134217952, 134217954, 134217956, 134217958, 134217960, 134217962, 134217964, 134217966, 134217968, 134217970, 134217972, 134217974, 134217976, 134217978, 134217980, 134217982, 134217984, 134217986, 134217988, 134217990, 134217992, 134217994, 134217996, 134217998, 134218000, 134218002, 134218004, 134218006, 134218008, 134218010, 134218012, 134218014, 134218016, 134218018, 134218020, 134218022, 134218024, 134218026, 201326892, 201326894, 201326896, 201326898, 201326900, 201326902, 201326904, 201326906, 201326908, 201326910, 201326912, 201326914, 201326916, 201326918, 201326920, 201326922, 201326924, 201326926, 201326928, 201326930, 201326932, 201326934, 201326936, 201326938, 201326940, 201326942, 201326944, 201326946, 201326948, 201326950, 201326952, 201326954, 201326956, 201326958, 201326960, 201326962, 201326964, 201326966, 201326968, 201326970, 201326972, 201326974, 201326976, 201326978, 201326980, 201326982, 201326984, 201326986, 201326988, 201326990, 268435856, 268435858, 268435860, 268435862, 268435864, 268435866, 268435868, 268435870, 268435872, 268435874, 268435876, 268435878, 268435880, 268435882, 268435884, 268435886, 268435888, 268435890, 268435892, 268435894, 268435896, 268435898, 268435900, 268435902, 268435904, 268435906, 268435908, 268435910, 268435912, 268435914, 268435916, 268435918, 268435920, 268435922, 268435924, 268435926, 268435928, 268435930, 268435932, 268435934, 268435936, 268435938, 268435940, 268435942, 268435944, 268435946, 268435948, 268435950, 268435952, 268435954, 335544820, 335544822, 335544824, 335544826, 335544828, 335544830 };
        long[] vals = Arrays.copyOf(keys, keys.length);
        long[] qbdata = new long[] { 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 67108964, 67108969, 67108974, 67108979, 67108984, 67108989, 67108994, 67108999, 67109004, 67109009, 67109014, 67109019, 67109024, 67109029, 67109034, 67109039, 67109044, 67109049, 67109054, 67109059, 134217928, 134217933, 134217938, 134217943, 134217948, 134217953, 134217958, 134217963, 134217968, 134217973, 134217978, 134217983, 134217988, 134217993, 134217998, 134218003, 134218008, 134218013, 134218018, 134218023, 201326892, 201326897, 201326902, 201326907, 201326912, 201326917, 201326922, 201326927, 201326932, 201326937, 201326942, 201326947, 201326952, 201326957, 201326962, 201326967, 201326972, 201326977, 201326982, 201326987, 268435856, 268435861, 268435866, 268435871, 268435876, 268435881, 268435886, 268435891, 268435896, 268435901, 268435906, 268435911, 268435916, 268435921, 268435926, 268435931, 268435936, 268435941, 268435946, 268435951, 335544820, 335544825, 335544830 };

        try (var writer = new SkipListWriter(docsFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (var pool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(pool, 0);
            var qb = new LongQueryBuffer(qbdata, qbdata.length);
            reader.retainData(qb);
            System.out.println(Arrays.toString(qb.copyData()));
        }
    }
}
