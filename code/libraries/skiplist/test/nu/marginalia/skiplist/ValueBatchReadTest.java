package nu.marginalia.skiplist;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.ffi.IoUring;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static nu.marginalia.skiplist.SkipListConstants.RECORD_SIZE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reading value blocks in io_uring batches must return what reading them one
 *  at a time returns, for query batches of every depth including those deeper
 *  than a single submission. */
class ValueBatchReadTest {
    static {
        System.setProperty("system.noSunMiscUnsafe", "TRUE");
    }

    private Path docsFile;
    private Path valuesFile;

    @BeforeEach
    void setUp() throws IOException {
        docsFile = Files.createTempFile(getClass().getSimpleName(), ".docs.dat");
        valuesFile = Files.createTempFile(getClass().getSimpleName(), ".values.dat");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(docsFile);
        Files.deleteIfExists(valuesFile);
    }

    private LongArray createArray(Arena arena, long[] keys, long[] values) {
        MemorySegment ms = arena.allocate(keys.length * RECORD_SIZE * 8L);
        for (int i = 0; i < keys.length; i++) {
            ms.setAtIndex(ValueLayout.JAVA_LONG, RECORD_SIZE * i, keys[i]);
            for (int vi = 1; vi < RECORD_SIZE; vi++) {
                ms.setAtIndex(ValueLayout.JAVA_LONG, RECORD_SIZE * i + vi, values[i] + vi);
            }
        }
        return LongArrayFactory.wrap(ms);
    }

    /** Drain a value reader into a flat array of every value of every record */
    private long[] drain(SkipListReader reader, Arena arena, long[] queryKeys, ValueBatchContext context)
            throws IOException
    {
        var valueReader = reader.getValueReader(arena, queryKeys, context);

        long[] out = new long[queryKeys.length * (RECORD_SIZE - 1)];
        int n = 0;
        while (valueReader.advance()) {
            for (int i = 0; i < RECORD_SIZE - 1; i++) {
                out[n++] = valueReader.getValue(i);
            }
        }

        return java.util.Arrays.copyOf(out, n);
    }

    private void assertBatchedMatchesSerial(int keyCount, int queryCount, long seed) throws IOException {
        Random r = new Random(seed);

        LongSortedSet keySet = new LongAVLTreeSet();
        while (keySet.size() < keyCount) {
            keySet.add(r.nextLong(0, 10_000_000));
        }
        long[] keys = keySet.toLongArray();

        Files.delete(docsFile);
        long offset;
        try (var writer = new SkipListWriter(docsFile, valuesFile);
             Arena arena = Arena.ofConfined()
        ) {
            offset = writer.writeList(createArray(arena, keys, keys), keys.length);
        }

        // Query a sorted subset, so the reader walks the list in order as it does in production
        LongSortedSet querySet = new LongAVLTreeSet();
        while (querySet.size() < Math.min(queryCount, keys.length)) {
            querySet.add(keys[r.nextInt(keys.length)]);
        }
        long[] queryKeys = querySet.toLongArray();

        try (var indexPool = new BufferPool(docsFile, SkipListConstants.BLOCK_SIZE, 32);
             var valuesReader = new SkipListValueReader(valuesFile);
             Arena arena = Arena.ofConfined()
        ) {
            long[] serial = drain(new SkipListReader(indexPool, valuesReader, offset), arena, queryKeys, null);

            long[] batched;
            try (var context = valuesReader.createBatchContext()) {
                batched = drain(new SkipListReader(indexPool, valuesReader, offset), arena, queryKeys, context);
            }

            assertEquals(queryKeys.length * (RECORD_SIZE - 1), serial.length,
                    "every queried key should have produced its values");
            assertArrayEquals(serial, batched);
        }
    }

    @Test
    void matchesSerialForOneBlock() throws IOException {
        Assumptions.assumeTrue(IoUring.isAvailable, "io_uring unavailable");
        assertBatchedMatchesSerial(64, 8, 1);
    }

    @Test
    void matchesSerialForABatchDeeperThanOneSubmission() throws IOException {
        Assumptions.assumeTrue(IoUring.isAvailable, "io_uring unavailable");
        // Enough keys that the values span far more blocks than BATCH_BLOCKS,
        // exercising the several-waves path
        assertBatchedMatchesSerial(50_000, 4 * ValueBatchContext.BATCH_BLOCKS * 64, 2);
    }

    @Test
    void matchesSerialAcrossSeeds() throws IOException {
        Assumptions.assumeTrue(IoUring.isAvailable, "io_uring unavailable");
        for (long seed = 3; seed < 13; seed++) {
            setUp();
            assertBatchedMatchesSerial(5_000, 500, seed);
            tearDown();
        }
    }
}
