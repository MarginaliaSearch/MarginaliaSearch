package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferPool;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static nu.marginalia.skiplist.SkipListConstants.RECORD_SIZE;
import static nu.marginalia.skiplist.SkipListConstants.VALUE_BLOCK_SIZE;

@Tag("slow")
public class SkipListFuzzTests {
    static {
        System.setProperty("system.noSunMiscUnsafe", "TRUE");
    }

    Path docsFile;
    Path valuesFile;

    @BeforeEach
    void setUp() throws IOException {
        docsFile = Files.createTempFile(SkipListWriterTest.class.getSimpleName(), ".docs.dat");
        valuesFile = Files.createTempFile(SkipListWriterTest.class.getSimpleName(), ".values.dat");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(docsFile);
        Files.deleteIfExists(valuesFile);
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

    LongArray createArray2v(Arena arena, long[] keys, long[] values1, long[] values2) {
        assert keys.length == values1.length && values1.length == values2.length;

        MemorySegment ms = arena.allocate(keys.length * RECORD_SIZE*8);
        for (int i = 0; i < keys.length; i++) {
            ms.setAtIndex(ValueLayout.JAVA_LONG, RECORD_SIZE*i, keys[i]);
            ms.setAtIndex(ValueLayout.JAVA_LONG, RECORD_SIZE * i + 1, values1[i]);
            ms.setAtIndex(ValueLayout.JAVA_LONG, RECORD_SIZE * i + 2, values2[i]);
        }
        return LongArrayFactory.wrap(ms);
    }

    @Test
    public void testRetainFuzz() throws IOException {

        for (int seed = 0; seed < 100; seed++) {
            System.out.println("Seed: " + seed);

            Random r = new Random(seed);

            int nKeys = 8; r.nextInt(100, 1000);
            LongSortedSet intersectionsSet = new LongAVLTreeSet();
            LongSortedSet keysSet = new LongAVLTreeSet();
            LongSortedSet qbSet = new LongAVLTreeSet();

            while (intersectionsSet.size() < 64) {
                long val = r.nextLong(0, 10_000);
                keysSet.add(val);
                qbSet.add(val);
                intersectionsSet.add(val);
            }
            while (keysSet.size() < nKeys) {
                long val = r.nextLong(0, 10_000);
                keysSet.add(val);
            }

            while (qbSet.size() < 512) {
                long val = r.nextLong(0, 10_000);
                if (keysSet.contains(val)) continue;

                qbSet.add(val);
            }

            long[] keys = keysSet.toLongArray();

            Files.delete(docsFile);
            try (var writer = new SkipListWriter(docsFile, valuesFile);
                 Arena arena = Arena.ofConfined()
            ) {
                writer.writeList(createArray(arena, keys, keys), 0, keys.length);
            }

            try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
                 var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 8)) {
                var reader = new SkipListReader(indexPool, valuePool, 0);
                LongQueryBuffer lqb = new LongQueryBuffer(qbSet.toLongArray(), qbSet.size());

                System.out.println("Keys: " + Arrays.toString(keysSet.toLongArray()));
                System.out.println("QB Input: " + Arrays.toString(qbSet.toLongArray()));

                reader.retainData(lqb);
                lqb.finalizeFiltering();
                long[] actual = lqb.copyData();
                long[] expected = intersectionsSet.toLongArray();


                System.out.println("Expected intersection: " + Arrays.toString(intersectionsSet.toLongArray()));
                System.out.println("Actual intersection: " + Arrays.toString(lqb.copyData()));
                Assertions.assertArrayEquals(expected, actual);
            }
        }
    }


    @Test
    public void testRetainFuzz1() throws IOException {

        long seedOffset = System.nanoTime();

        for (int seed = 0; seed < 100; seed++) {
            System.out.println("Seed: " + (seed + seedOffset));

            Random r = new Random(seed + seedOffset);

            LongSortedSet keyset = new LongAVLTreeSet();

            int nkeys = r.nextInt(SkipListConstants.BLOCK_SIZE/2, SkipListConstants.BLOCK_SIZE*4);
            while (keyset.size() < nkeys) {
                long val = r.nextLong(0, 10_000_000);

                keyset.add(val);
            }

            long[] keys = keyset.toLongArray();
            long[] qbs = new long[] { keys[r.nextInt(0, keys.length)] };

            long off = 0;
            Files.delete(docsFile);
            try (var writer = new SkipListWriter(docsFile, valuesFile);
                 Arena arena = Arena.ofConfined()
            ) {
                writer.padDocuments(8*r.nextInt(0, SkipListConstants.BLOCK_SIZE/8));
                off = writer.writeList(createArray(arena, keys, keys), 0, keys.length);
            }

            try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
                 var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 8)) {
                var reader = new SkipListReader(indexPool, valuePool, off);
                LongQueryBuffer lqb = new LongQueryBuffer(qbs, 1);

                reader.retainData(lqb);
                lqb.finalizeFiltering();
                long[] actual = lqb.copyData();
                long[] expected = qbs;


                System.out.println(Arrays.toString(expected));
                System.out.println(Arrays.toString(actual));
                Assertions.assertArrayEquals(expected, actual);
            }
        }
    }

    @Test
    public void testRejectFuzz1() throws IOException {

        long seedOffset = System.nanoTime();
        for (int seed = 0; seed < 100; seed++) {
            System.out.println("Seed: " + (seed + seedOffset));

            Random r = new Random(seed + seedOffset);

            LongSortedSet keyset = new LongAVLTreeSet();

            int nkeys = r.nextInt(SkipListConstants.BLOCK_SIZE/2, SkipListConstants.BLOCK_SIZE*4);
            while (keyset.size() < nkeys) {
                long val = r.nextLong(0, 10_000_000);

                keyset.add(val);
            }

            long[] keys = keyset.toLongArray();
            long[] qbs = new long[] { keys[r.nextInt(0, keys.length)] };

            long off = 0;
            Files.delete(docsFile);
            try (var writer = new SkipListWriter(docsFile, valuesFile);
                 Arena arena = Arena.ofConfined()
            ) {
                writer.padDocuments(8*r.nextInt(0, SkipListConstants.BLOCK_SIZE/8));
                off = writer.writeList(createArray(arena, keys, keys), 0, keys.length);
            }

            try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
                 var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 8)) {

                var reader = new SkipListReader(indexPool, valuePool, off);
                LongQueryBuffer lqb = new LongQueryBuffer(qbs, 1);

                reader.rejectData(lqb);
                lqb.finalizeFiltering();
                long[] actual = lqb.copyData();
                long[] expected = new long[0];


                System.out.println(Arrays.toString(expected));
                System.out.println(Arrays.toString(actual));
                Assertions.assertArrayEquals(expected, actual);
            }
        }
    }

    @Tag("slow")
    @Test
    public void testGetKeysFuzz() throws IOException {

        for (int seed = 0; seed < 256; seed++) {
            System.out.println("Seed: " + seed);

            Random r = new Random(seed);

            int nKeys = r.nextInt(100, 20000);
            LongSortedSet intersectionsSet = new LongAVLTreeSet();
            LongSortedSet keysSet = new LongAVLTreeSet();
            LongSortedSet qbSet = new LongAVLTreeSet();

            while (intersectionsSet.size() < 64) {
                long val = r.nextLong(1, 1_000_000);
                keysSet.add(val);
                qbSet.add(val);
                intersectionsSet.add(val);
            }
            while (keysSet.size() < nKeys) {
                long val = r.nextLong(1, 1_000_000);
                keysSet.add(val);
            }

            while (qbSet.size() < 512) {
                long val = r.nextLong(1, 1_000_000);
                if (keysSet.contains(val)) continue;

                qbSet.add(val);
            }

            long[] keys = keysSet.toLongArray();

            long blockStart;
            Files.delete(docsFile);
            Files.delete(valuesFile);
            try (var writer = new SkipListWriter(docsFile, valuesFile);
                 Arena arena = Arena.ofConfined()
            ) {
                writer.padDocuments(r.nextInt(0, 4096/8) * 8);
                blockStart = writer.writeList(createArray(arena, keys, keys), 0, keys.length);
            }

            try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
                 var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 8)) {

                var reader = new SkipListReader(indexPool, valuePool, blockStart);
                try (var page = indexPool.get(blockStart & -SkipListConstants.BLOCK_SIZE)) {
                    reader.parseBlock(page.getMemorySegment(), (int) blockStart & (SkipListConstants.BLOCK_SIZE - 1));
                }

                long[] queryKeys = qbSet.toLongArray();
                long[] queryVals = reader.getAllValues(queryKeys);

                LongSortedSet presentValues = new LongAVLTreeSet();
                for (int i = 0; i < queryKeys.length; i++) {
                    if (queryVals[i] != 0) {
                        presentValues.add(queryKeys[i]);
                    }

                }

                System.out.println("Keys: " + Arrays.toString(keysSet.toLongArray()));
                System.out.println("QB Input: " + Arrays.toString(qbSet.toLongArray()));

                long[] actual = presentValues.toLongArray();
                long[] expected = intersectionsSet.toLongArray();

                System.out.println("Expected intersection: " + Arrays.toString(intersectionsSet.toLongArray()));
                System.out.println("Actual intersection: " + Arrays.toString(presentValues.toLongArray()));
                Assertions.assertArrayEquals(expected, actual);
            }
        }
    }

    @Test
    @Tag("slow")
    public void testParseFuzz() throws IOException {

        long seedOffset = System.nanoTime();
        for (int seed = 0; seed < 100; seed++) {
            System.out.println("Seed: " + (seed + seedOffset));

            Random r = new Random(seed);

            List<long[]> keysForBlocks = new ArrayList<>();

            for (int i = 0; i < 1000; i++) {

                int nVals = r.nextInt(8, SkipListConstants.MAX_RECORDS_PER_BLOCK);
                long[] keys = new long[nVals];
                for (int ki = 0; ki < keys.length; ki++) {
                    keys[ki] = r.nextLong(0, Long.MAX_VALUE);
                }

                Arrays.sort(keys);
                keysForBlocks.add(keys);
            }
            List<Long> offsets = new ArrayList<>();
            Files.delete(docsFile);
            try (var writer = new SkipListWriter(docsFile, valuesFile);
                 Arena arena = Arena.ofConfined()
            ) {
                writer.padDocuments(r.nextInt(0, SkipListConstants.BLOCK_SIZE/8) * 8);
                for (var block : keysForBlocks) {
                    offsets.add(writer.writeList(createArray(arena, block, block), 0, block.length));
                }
            }

            try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
                 var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 8)) {
                for (var offset: offsets) {
                    var reader = new SkipListReader(indexPool, valuePool, offset);
                    reader.parseBlocks(indexPool, offset);
                }
            }
        }
    }

    @Test
    public void testGetAllValues__largeBlock__fuzz2() throws IOException {
        long[] keys = LongStream.range(0, 32000).map(v -> 2*v).toArray();
        long[] vals = LongStream.range(0, 32000).map(v -> -2*v).toArray();

        try (var writer = new SkipListWriter(docsFile, valuesFile)) {
            writer.writeList(createArray(keys, vals), 0, keys.length);
        }

        Random r = new Random();

        try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
             var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 8)) {
            var reader = new SkipListReader(indexPool, valuePool, 0);

            for (int i = 0; i < 1000; i++) {
                long[] queryKeys = new long[]{r.nextLong(0, 32000) * 2, r.nextLong(0, 32000) * 2};
                Arrays.sort(queryKeys);
                long[] expectedVals = new long[]{-queryKeys[0], -queryKeys[1], -queryKeys[0], -queryKeys[1]};

                long[] queryVals = reader.getAllValues(queryKeys);

                System.out.println(Arrays.toString(expectedVals));
                System.out.println(Arrays.toString(queryVals));

                Assertions.assertArrayEquals(expectedVals, queryVals);

                reader.reset();
            }
        }
    }


    @Test
    public void testGetAllValues__largeBlock__fuzz2vs() throws IOException {
        long seed = System.nanoTime();
        Random r = new Random(seed);

        for (int size : IntStream.generate(() -> r.nextInt(1,65536)).limit(50).toArray()) {

            System.out.println("Case = " + size + ":" + seed);
            long[] keys = LongStream.range(0, size).map(v -> 2 * v).toArray();
            long[] vals = LongStream.range(0, size).map(v -> -2 * v).toArray();

            Files.deleteIfExists(docsFile);

            try (var writer = new SkipListWriter(docsFile, valuesFile)) {
                writer.writeList(createArray(keys, vals), 0, keys.length);
            }


            try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 8);
                 var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 8)) {
                var reader = new SkipListReader(indexPool, valuePool, 0);

                for (int iter = 0; iter < 100_000; iter++) {
                    long[] queryKeys = new long[]{r.nextLong(0, size) * 2, r.nextLong(0, size) * 2, 0, 0};
                    queryKeys[2] = queryKeys[0] + r.nextInt(1,8000);
                    queryKeys[3] = queryKeys[1] + r.nextInt(1,8000);

                    Arrays.sort(queryKeys);
                    long[] expectedVals = new long[queryKeys.length*2];

                    for (int j = 0; j < queryKeys.length; j++) {
                        if ((queryKeys[j] % 2) == 0 && queryKeys[j] < 2*size) {
                            expectedVals[j] = -queryKeys[j];
                            expectedVals[queryKeys.length + j] = -queryKeys[j];
                        }
                    }

                    long[] queryVals = reader.getAllValues(queryKeys);

                    // Keep the logs clean
                    if (!Arrays.equals(expectedVals, queryVals)) {
                        System.out.println(Arrays.toString(queryKeys));
                        System.out.println(Arrays.toString(expectedVals));
                        System.out.println(Arrays.toString(queryVals));

                        Assertions.assertArrayEquals(expectedVals, queryVals);
                    }

                    reader.reset();
                }
            }
        }
    }

    @Test
    public void testGetAllValues__largeBlock__fuzz2vs_two() throws IOException {
        long seed = System.nanoTime();
        Random r = new Random(seed);

        for (int size : IntStream.generate(() -> r.nextInt(1,65536)).limit(500).toArray()) {

            System.out.println("Case = " + size + ":" + seed);
            long[] keys = LongStream.range(0, size).map(v -> 2 * v).toArray();
            long[] vals1 = LongStream.range(0, size).map(v -> -2 * v).toArray();
            long[] vals2 = LongStream.range(0, size).map(v -> ~(2*v)).toArray();

            Files.deleteIfExists(docsFile);
            Files.deleteIfExists(valuesFile);

            try (var writer = new SkipListWriter(docsFile, valuesFile); Arena a = Arena.ofConfined()) {
                writer.writeList(createArray2v(a, keys, vals1, vals2), 0, keys.length);
            }


            try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 512);
                 var valuePool = new BufferPool(valuesFile, SkipListConstants.VALUE_BLOCK_SIZE, 512)) {
                var reader = new SkipListReader(indexPool, valuePool, 0);

                long[] queryKeys = new long[64];
                long[] expectedVals = new long[128];

                for (int iter = 0; iter < 10000; iter++) {

                    for (int i = 0; i < 16; i++) {
                        queryKeys[i] = r.nextLong(0, size) * 2;
                        queryKeys[16+i] = queryKeys[i] + r.nextLong(0, VALUE_BLOCK_SIZE);
                        queryKeys[24+i] = queryKeys[i] + r.nextLong(0, VALUE_BLOCK_SIZE);
                        queryKeys[32+i] = queryKeys[i] + r.nextLong(0, VALUE_BLOCK_SIZE);
                    }

                    Arrays.sort(queryKeys);

                    for (int j = 0; j < queryKeys.length; j++) {
                        if ((queryKeys[j] % 2) == 0 && queryKeys[j] < 2*size) {
                            expectedVals[j] = -queryKeys[j];
                            expectedVals[queryKeys.length + j] = ~queryKeys[j];
                        }
                        else {
                            expectedVals[j] = expectedVals[queryKeys.length + j] = 0;
                        }
                    }

                    long[] queryVals = reader.getAllValues(queryKeys);

                    // Keep the logs clean
                    if (!Arrays.equals(expectedVals, queryVals)) {
                        System.out.println(Arrays.toString(queryKeys));
                        System.out.println(Arrays.toString(expectedVals));
                        System.out.println(Arrays.toString(queryVals));

                        Assertions.assertArrayEquals(expectedVals, queryVals);
                    }

                    reader.reset();
                }
            }
        }
    }

}
