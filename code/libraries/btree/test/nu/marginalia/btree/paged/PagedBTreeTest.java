package nu.marginalia.btree.paged;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class PagedBTreeTest {

    static {
        System.setProperty("system.noSunMiscUnsafe", "TRUE");
    }

    Path btreeFile;

    @BeforeEach
    void setUp() throws IOException {
        btreeFile = Files.createTempFile("btree-test", ".dat");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(btreeFile);
    }

    // ---------------------------------------------------------------
    // Writer validation tests
    // ---------------------------------------------------------------

    @Test
    void writerRejectsUnsortedInput() {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        assertThrows(IllegalArgumentException.class, () ->
            writer.write(3, sink -> {
                sink.put(10, 100);
                sink.put(5, 50);
                sink.put(20, 200);
            })
        );
    }

    @Test
    void writerRejectsDuplicateKeys() {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        assertThrows(IllegalArgumentException.class, () ->
            writer.write(3, sink -> {
                sink.put(10, 100);
                sink.put(10, 200);
                sink.put(20, 300);
            })
        );
    }

    @Test
    void writerRejectsWrongEntryCount() {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        assertThrows(IllegalStateException.class, () ->
            writer.write(3, sink -> {
                sink.put(10, 100);
                sink.put(20, 200);
            })
        );
    }

    @Test
    void writerRejectsTinyPageSize() {
        assertThrows(IllegalArgumentException.class, () ->
            new PagedBTreeWriter(btreeFile, 256, 2)
        );
    }

    @Test
    void writerRejectsNonPowerOfTwoPageSize() {
        assertThrows(IllegalArgumentException.class, () ->
            new PagedBTreeWriter(btreeFile, 1000, 2)
        );
    }

    // ---------------------------------------------------------------
    // Empty tree
    // ---------------------------------------------------------------

    @Test
    void emptyTree() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(0, sink -> {});

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(0, reader.numEntries());
            assertEquals(-1, reader.findEntry(42));
            assertEquals(-1, reader.getValue(42));
        }
    }

    // ---------------------------------------------------------------
    // Single entry
    // ---------------------------------------------------------------

    @Test
    void singleEntry() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(1, sink -> sink.put(42, 999));

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(1, reader.numEntries());
            assertEquals(2, reader.entrySize());

            long idx = reader.findEntry(42);
            assertTrue(idx >= 0);
            assertEquals(999, reader.getEntryValue(idx));

            assertEquals(999, reader.getValue(42));

            assertEquals(-1, reader.findEntry(41));
            assertEquals(-1, reader.findEntry(43));
            assertEquals(-1, reader.findEntry(0));
            assertEquals(-1, reader.findEntry(Long.MAX_VALUE));
        }
    }

    // ---------------------------------------------------------------
    // Small trees (fit in one leaf)
    // ---------------------------------------------------------------

    @Test
    void smallTrees() throws IOException {
        for (int n : new int[]{2, 3, 5, 10}) {
            long[] keys = LongStream.range(0, n).map(i -> i * 10 + 1).toArray();
            long[] values = LongStream.range(0, n).map(i -> i * 100).toArray();

            var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
            writer.write(n, sink -> {
                for (int i = 0; i < n; i++) {
                    sink.put(keys[i], values[i]);
                }
            });

            try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
                assertEquals(n, reader.numEntries());

                for (int i = 0; i < n; i++) {
                    long idx = reader.findEntry(keys[i]);
                    assertTrue(idx >= 0, "Key " + keys[i] + " not found (n=" + n + ")");
                    assertEquals(values[i], reader.getEntryValue(idx));
                }

                assertEquals(-1, reader.findEntry(0));
                assertEquals(-1, reader.findEntry(keys[0] - 1));
                assertEquals(-1, reader.findEntry(keys[n-1] + 1));
                assertEquals(-1, reader.findEntry(keys[0] + 1));
            }
        }
    }

    // ---------------------------------------------------------------
    // Medium trees (multiple leaves, one or two internal levels)
    // ---------------------------------------------------------------

    @Test
    void mediumTreesEntrySize2() throws IOException {
        for (int n : new int[]{100, 200, 500, 1000}) {
            long[] keys = generateSortedDistinctKeys(n, 12345L + n);
            long[] values = new long[n];
            for (int i = 0; i < n; i++) {
                values[i] = keys[i] * 7 + 3;
            }

            var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
            writer.write(n, sink -> {
                for (int i = 0; i < n; i++) {
                    sink.put(keys[i], values[i]);
                }
            });

            try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
                assertEquals(n, reader.numEntries());

                for (int i = 0; i < n; i++) {
                    long idx = reader.findEntry(keys[i]);
                    assertTrue(idx >= 0, "Key " + keys[i] + " not found at i=" + i + " (n=" + n + ")");
                    assertEquals(values[i], reader.getEntryValue(idx),
                            "Wrong value for key " + keys[i]);
                }

                Random rng = new Random(99999);
                Set<Long> keySet = new HashSet<>();
                for (long k : keys) keySet.add(k);
                for (int i = 0; i < 500; i++) {
                    long randomKey = rng.nextLong() & Long.MAX_VALUE;
                    if (keySet.contains(randomKey)) continue;
                    assertEquals(-1, reader.findEntry(randomKey),
                            "False positive for key " + randomKey);
                }
            }
        }
    }

    @Test
    void mediumTreesEntrySize1() throws IOException {
        for (int n : new int[]{100, 500, 1000}) {
            long[] keys = generateSortedDistinctKeys(n, 54321L + n);

            var writer = new PagedBTreeWriter(btreeFile, 4096, 1);
            writer.write(n, sink -> {
                for (long key : keys) {
                    sink.put(key, 0);
                }
            });

            try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
                assertEquals(n, reader.numEntries());
                assertEquals(1, reader.entrySize());

                for (long key : keys) {
                    long idx = reader.findEntry(key);
                    assertTrue(idx >= 0, "Key " + key + " not found (n=" + n + ")");
                }

                assertEquals(-1, reader.findEntry(0));
                assertEquals(-1, reader.findEntry(Long.MAX_VALUE));
            }
        }
    }

    // ---------------------------------------------------------------
    // Large trees (forces multi-level internal nodes)
    // ---------------------------------------------------------------

    @Test
    void largeTreeDefaultPageSize() throws IOException {
        int n = 50_000;
        long[] keys = generateSortedDistinctKeys(n, 77777L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = i;

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], values[i]);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 16)) {
            assertEquals(n, reader.numEntries());

            Random rng = new Random(11111);
            for (int trial = 0; trial < 5000; trial++) {
                int idx = rng.nextInt(n);
                long found = reader.findEntry(keys[idx]);
                assertTrue(found >= 0, "Key " + keys[idx] + " not found");
                assertEquals(values[idx], reader.getEntryValue(found));
            }
        }
    }

    @Test
    void largeTreeSmallPageSize() throws IOException {
        int n = 5000;
        long[] keys = generateSortedDistinctKeys(n, 33333L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = keys[i] + 1;

        var writer = new PagedBTreeWriter(btreeFile, 512, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], values[i]);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 16)) {
            assertEquals(n, reader.numEntries());
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], reader.getValue(keys[i]),
                        "Wrong value at i=" + i);
            }
        }
    }

    // ---------------------------------------------------------------
    // Page size variations
    // ---------------------------------------------------------------

    @Test
    void variousPageSizes() throws IOException {
        for (int pageSize : new int[]{512, 1024, 2048, 4096, 8192}) {
            int n = 500;
            long[] keys = generateSortedDistinctKeys(n, pageSize);

            var writer = new PagedBTreeWriter(btreeFile, pageSize, 2);
            writer.write(n, sink -> {
                for (int i = 0; i < n; i++) {
                    sink.put(keys[i], (long) i);
                }
            });

            try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
                assertEquals(n, reader.numEntries());
                for (int i = 0; i < n; i++) {
                    assertEquals(i, reader.getValue(keys[i]),
                            "Failed at i=" + i + " pageSize=" + pageSize);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Boundary key values
    // ---------------------------------------------------------------

    @Test
    void extremeKeyValues() throws IOException {
        long[] keys = {1, 2, Long.MAX_VALUE / 2, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        long[] values = {10, 20, 30, 40, 50};

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(keys.length, sink -> {
            for (int i = 0; i < keys.length; i++) {
                sink.put(keys[i], values[i]);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            for (int i = 0; i < keys.length; i++) {
                assertEquals(values[i], reader.getValue(keys[i]),
                        "Failed for key " + keys[i]);
            }
            assertEquals(-1, reader.findEntry(0));
            assertEquals(-1, reader.findEntry(3));
        }
    }

    // ---------------------------------------------------------------
    // queryData tests
    // ---------------------------------------------------------------

    @Test
    void queryDataEntrySize2() throws IOException {
        int n = 100;
        long[] keys = generateSortedDistinctKeys(n, 42L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = keys[i] * 3;

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], values[i]);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            long[] queryKeys = {keys[0], keys[10], keys[50], keys[99]};
            long[] result = reader.queryData(queryKeys, 1);

            assertEquals(values[0], result[0]);
            assertEquals(values[10], result[1]);
            assertEquals(values[50], result[2]);
            assertEquals(values[99], result[3]);
        }
    }

    @Test
    void queryDataMissingKeys() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(3, sink -> {
            sink.put(10, 100);
            sink.put(20, 200);
            sink.put(30, 300);
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            long[] queryKeys = {5, 15, 25, 35};
            long[] result = reader.queryData(queryKeys, 1);

            assertEquals(0, result[0]);
            assertEquals(0, result[1]);
            assertEquals(0, result[2]);
            assertEquals(0, result[3]);
        }
    }

    @Test
    void queryDataMixedHitAndMiss() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(3, sink -> {
            sink.put(10, 100);
            sink.put(20, 200);
            sink.put(30, 300);
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            long[] queryKeys = {10, 15, 20, 25, 30};
            long[] result = reader.queryData(queryKeys, 1);

            assertEquals(100, result[0]);
            assertEquals(0, result[1]);
            assertEquals(200, result[2]);
            assertEquals(0, result[3]);
            assertEquals(300, result[4]);
        }
    }

    // ---------------------------------------------------------------
    // calculateSize tests
    // ---------------------------------------------------------------

    @Test
    void calculateSizeConsistency() throws IOException {
        for (int n : new int[]{0, 1, 10, 100, 1000, 5000}) {
            var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
            long estimated = writer.calculateSize(n);

            if (n > 0) {
                writer.write(n, sink -> {
                    for (int i = 0; i < n; i++) {
                        sink.put((long) i * 2 + 1, (long) i);
                    }
                });

                long actual = Files.size(btreeFile);
                assertEquals(estimated, actual,
                        "Size mismatch for n=" + n +
                        ": estimated=" + estimated + ", actual=" + actual);
            }
        }
    }

    @Test
    void calculateSizeSmallPageSize() {
        var writer = new PagedBTreeWriter(btreeFile, 512, 2);
        long size = writer.calculateSize(1000);
        assertTrue(size > 0);
    }

    // ---------------------------------------------------------------
    // Exact leaf boundary tests
    // ---------------------------------------------------------------

    @Test
    void exactlyOneLeafCapacity() throws IOException {
        int pageSize = 4096;
        int entrySize = 2;
        int leafCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES) / (entrySize * 8);

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(leafCapacity, sink -> {
            for (int i = 0; i < leafCapacity; i++) {
                sink.put(i + 1, i * 10L);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(leafCapacity, reader.numEntries());
            for (int i = 0; i < leafCapacity; i++) {
                assertEquals(i * 10L, reader.getValue(i + 1));
            }
        }
    }

    @Test
    void oneMoreThanLeafCapacity() throws IOException {
        int pageSize = 4096;
        int entrySize = 2;
        int leafCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES) / (entrySize * 8);
        int n = leafCapacity + 1;

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i + 1, i * 10L);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(n, reader.numEntries());
            for (int i = 0; i < n; i++) {
                assertEquals(i * 10L, reader.getValue(i + 1),
                        "Failed at i=" + i);
            }
        }
    }

    // ---------------------------------------------------------------
    // Consecutive keys
    // ---------------------------------------------------------------

    @Test
    void consecutiveKeys() throws IOException {
        int n = 2000;
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i + 1, (long) i * i);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            for (int i = 0; i < n; i++) {
                assertEquals((long) i * i, reader.getValue(i + 1));
            }
            assertEquals(-1, reader.findEntry(0));
            assertEquals(-1, reader.findEntry(n + 1));
        }
    }

    // ---------------------------------------------------------------
    // Sparse keys (large gaps)
    // ---------------------------------------------------------------

    @Test
    void sparseKeys() throws IOException {
        int n = 500;
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            keys[i] = (long) i * 1_000_000 + 1;
        }

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], (long) i);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            for (int i = 0; i < n; i++) {
                assertEquals(i, reader.getValue(keys[i]));
                assertEquals(-1, reader.findEntry(keys[i] - 1));
            }
        }
    }

    // ---------------------------------------------------------------
    // Fuzz test
    // ---------------------------------------------------------------

    @RepeatedTest(10)
    void fuzzTest() throws IOException {
        Random rng = new Random();
        int n = rng.nextInt(1, 3000);
        int pageSize = 1 << (rng.nextInt(4) + 9); // 512, 1024, 2048, 4096
        int entrySize = rng.nextBoolean() ? 1 : 2;

        long[] keys = generateSortedDistinctKeys(n, rng.nextLong());
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = rng.nextLong();

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], entrySize > 1 ? values[i] : 0);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(n, reader.numEntries());

            for (int i = 0; i < n; i++) {
                long idx = reader.findEntry(keys[i]);
                assertTrue(idx >= 0, "Key " + keys[i] + " not found (n=" + n + ", page=" + pageSize + ")");
                if (entrySize > 1) {
                    assertEquals(values[i], reader.getEntryValue(idx));
                }
            }

            Set<Long> keySet = new HashSet<>();
            for (long k : keys) keySet.add(k);
            for (int trial = 0; trial < 200; trial++) {
                long rk = rng.nextLong() & Long.MAX_VALUE;
                if (rk == 0) rk = 1;
                if (!keySet.contains(rk)) {
                    assertEquals(-1, reader.findEntry(rk));
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Two entries
    // ---------------------------------------------------------------

    @Test
    void twoEntries() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(2, sink -> {
            sink.put(100, 1000);
            sink.put(200, 2000);
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(2, reader.numEntries());
            assertEquals(1000, reader.getValue(100));
            assertEquals(2000, reader.getValue(200));
            assertEquals(-1, reader.findEntry(99));
            assertEquals(-1, reader.findEntry(101));
            assertEquals(-1, reader.findEntry(150));
            assertEquals(-1, reader.findEntry(201));
        }
    }

    // ---------------------------------------------------------------
    // File size matches estimate for various counts
    // ---------------------------------------------------------------

    @Test
    void fileSizeMatchesEstimate() throws IOException {
        for (int n : new int[]{1, 5, 50, 500, 5000}) {
            int pageSize = 512;
            var writer = new PagedBTreeWriter(btreeFile, pageSize, 2);

            long estimated = writer.calculateSize(n);
            writer.write(n, sink -> {
                for (int i = 0; i < n; i++) {
                    sink.put((long) i * 3 + 1, (long) i);
                }
            });

            long actual = Files.size(btreeFile);
            assertEquals(estimated, actual,
                    "Size estimate mismatch for n=" + n);
        }
    }

    // ---------------------------------------------------------------
    // First and last key lookups
    // ---------------------------------------------------------------

    @Test
    void firstAndLastKey() throws IOException {
        int n = 1000;
        long[] keys = generateSortedDistinctKeys(n, 99L);

        var writer = new PagedBTreeWriter(btreeFile, 512, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], (long) i);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(0, reader.getValue(keys[0]));
            assertEquals(n - 1, reader.getValue(keys[n - 1]));
        }
    }

    // ---------------------------------------------------------------
    // getValue convenience method
    // ---------------------------------------------------------------

    @Test
    void getValueReturnsMinusOneForMissing() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(1, sink -> sink.put(42, 100));

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(100, reader.getValue(42));
            assertEquals(-1, reader.getValue(41));
            assertEquals(-1, reader.getValue(43));
        }
    }

    // ---------------------------------------------------------------
    // Internal node boundary (exactly fills an internal node's children)
    // ---------------------------------------------------------------

    @Test
    void exactlyFillsInternalNode() throws IOException {
        int pageSize = 512;
        int entrySize = 2;
        int leafCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES) / (entrySize * 8);
        int internalCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES - 8) / 16;
        int n = leafCapacity * (internalCapacity + 1);

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i + 1, (long) i);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(n, reader.numEntries());
            for (int i = 0; i < n; i++) {
                assertEquals(i, reader.getValue(i + 1),
                        "Wrong value at i=" + i);
            }
        }
    }

    @Test
    void oneMoreThanInternalNodeCapacity() throws IOException {
        int pageSize = 512;
        int entrySize = 2;
        int leafCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES) / (entrySize * 8);
        int internalCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES - 8) / 16;
        int n = leafCapacity * (internalCapacity + 1) + 1;

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i + 1, (long) i);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(n, reader.numEntries());
            for (int i = 0; i < n; i++) {
                assertEquals(i, reader.getValue(i + 1),
                        "Wrong value at i=" + i);
            }
        }
    }

    // ---------------------------------------------------------------
    // Negative values (value can be any long)
    // ---------------------------------------------------------------

    @Test
    void negativeValues() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(3, sink -> {
            sink.put(1, -100);
            sink.put(2, Long.MIN_VALUE);
            sink.put(3, -1);
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(-100, reader.getValue(1));
            assertEquals(Long.MIN_VALUE, reader.getValue(2));
            assertEquals(-1, reader.getValue(3));
        }
    }

    // ---------------------------------------------------------------
    // Buffered reader tests
    // ---------------------------------------------------------------

    @Test
    void bufferedEmptyTree() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(0, sink -> {});

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            assertEquals(0, reader.numEntries());
            assertEquals(-1, reader.findEntry(42));
            assertEquals(-1, reader.getValue(42));
        }
    }

    @Test
    void bufferedSingleEntry() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(1, sink -> sink.put(42, 999));

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            assertEquals(1, reader.numEntries());
            assertEquals(999, reader.getValue(42));
            assertEquals(-1, reader.findEntry(41));
        }
    }

    @Test
    void bufferedMediumTree() throws IOException {
        int n = 500;
        long[] keys = generateSortedDistinctKeys(n, 88888L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = keys[i] * 7 + 3;

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], values[i]);
            }
        });

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            assertEquals(n, reader.numEntries());
            for (int i = 0; i < n; i++) {
                assertEquals(values[i], reader.getValue(keys[i]),
                        "Wrong value for key " + keys[i]);
            }

            assertEquals(-1, reader.findEntry(0));
            assertEquals(-1, reader.findEntry(Long.MAX_VALUE));
        }
    }

    @Test
    void bufferedLargeTree() throws IOException {
        int n = 50_000;
        long[] keys = generateSortedDistinctKeys(n, 77777L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = i;

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], values[i]);
            }
        });

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            assertEquals(n, reader.numEntries());

            Random rng = new Random(11111);
            for (int trial = 0; trial < 5000; trial++) {
                int idx = rng.nextInt(n);
                assertEquals(values[idx], reader.getValue(keys[idx]));
            }
        }
    }

    @Test
    void bufferedQueryData() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(3, sink -> {
            sink.put(10, 100);
            sink.put(20, 200);
            sink.put(30, 300);
        });

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            long[] result = reader.queryData(new long[]{10, 15, 20, 25, 30}, 1);
            assertEquals(100, result[0]);
            assertEquals(0, result[1]);
            assertEquals(200, result[2]);
            assertEquals(0, result[3]);
            assertEquals(300, result[4]);
        }
    }

    @Test
    void bufferedLeafBoundary() throws IOException {
        int pageSize = 512;
        int entrySize = 2;
        int leafCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES) / (entrySize * 8);
        int n = leafCapacity + 1;

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i + 1, i * 10L);
            }
        });

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            assertEquals(n, reader.numEntries());
            for (int i = 0; i < n; i++) {
                assertEquals(i * 10L, reader.getValue(i + 1),
                        "Failed at i=" + i);
            }
        }
    }

    @RepeatedTest(10)
    void bufferedFuzzTest() throws IOException {
        Random rng = new Random();
        int n = rng.nextInt(1, 3000);
        int pageSize = 1 << (rng.nextInt(4) + 9);
        int entrySize = rng.nextBoolean() ? 1 : 2;

        long[] keys = generateSortedDistinctKeys(n, rng.nextLong());
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = rng.nextLong();

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], entrySize > 1 ? values[i] : 0);
            }
        });

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            assertEquals(n, reader.numEntries());
            for (int i = 0; i < n; i++) {
                long idx = reader.findEntry(keys[i]);
                assertTrue(idx >= 0, "Key " + keys[i] + " not found");
                if (entrySize > 1) {
                    assertEquals(values[i], reader.getEntryValue(idx));
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Key = 0 (boundary condition)
    // ---------------------------------------------------------------

    @Test
    void keyZero() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(3, sink -> {
            sink.put(0, 999);
            sink.put(1, 888);
            sink.put(2, 777);
        });

        // Zero was rejected by the sort-check (prev >= curr) if key 0
        // caused issues with unsigned comparison or similar
        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            assertEquals(999, reader.getValue(0));
            assertEquals(888, reader.getValue(1));
            assertEquals(777, reader.getValue(2));
        }

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            assertEquals(999, reader.getValue(0));
            assertEquals(888, reader.getValue(1));
            assertEquals(777, reader.getValue(2));
        }
    }

    @Test
    void keyZeroInLargeTree() throws IOException {
        int n = 1000;
        var writer = new PagedBTreeWriter(btreeFile, 512, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i, i * 10L);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(0, reader.getValue(0));
            assertEquals(10, reader.getValue(1));
            assertEquals((n - 1) * 10L, reader.getValue(n - 1));
            assertEquals(-1, reader.findEntry(n));
        }
    }

    // ---------------------------------------------------------------
    // Concurrent reads
    // ---------------------------------------------------------------

    @Test
    void concurrentReadsDirect() throws Exception {
        int n = 10_000;
        long[] keys = generateSortedDistinctKeys(n, 42424242L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = keys[i] * 3 + 1;

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) sink.put(keys[i], values[i]);
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 16)) {
            concurrentLookups(reader, keys, values, n);
        }
    }

    @Test
    void concurrentReadsBuffered() throws Exception {
        int n = 10_000;
        long[] keys = generateSortedDistinctKeys(n, 42424242L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = keys[i] * 3 + 1;

        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) sink.put(keys[i], values[i]);
        });

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            concurrentLookups(reader, keys, values, n);
        }
    }

    private void concurrentLookups(PagedBTreeReader reader, long[] keys, long[] values, int n) throws Exception {
        int numThreads = 8;
        int lookupsPerThread = 2000;
        var errors = new java.util.concurrent.atomic.AtomicInteger();
        var latch = new java.util.concurrent.CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            int threadId = t;
            Thread.ofPlatform().start(() -> {
                try {
                    Random rng = new Random(threadId * 31L);
                    for (int i = 0; i < lookupsPerThread; i++) {
                        int idx = rng.nextInt(n);
                        long val = reader.getValue(keys[idx]);
                        if (val != values[idx]) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, errors.get(), "Concurrent lookups produced wrong results");
    }

    // ---------------------------------------------------------------
    // Both reader modes produce identical results
    // ---------------------------------------------------------------

    @Test
    void bothModesIdenticalResults() throws IOException {
        int n = 2000;
        long[] keys = generateSortedDistinctKeys(n, 55555L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = keys[i] ^ 0xDEADBEEFL;

        var writer = new PagedBTreeWriter(btreeFile, 512, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) sink.put(keys[i], values[i]);
        });

        try (var directReader = PagedBTreeReader.direct(btreeFile, 16);
             var bufferedReader = PagedBTreeReader.buffered(btreeFile)) {

            assertEquals(directReader.numEntries(), bufferedReader.numEntries());
            assertEquals(directReader.entrySize(), bufferedReader.entrySize());

            for (int i = 0; i < n; i++) {
                long directIdx = directReader.findEntry(keys[i]);
                long bufferedIdx = bufferedReader.findEntry(keys[i]);
                assertEquals(directIdx, bufferedIdx,
                        "findEntry mismatch for key " + keys[i]);
                assertEquals(directReader.getEntryValue(directIdx),
                        bufferedReader.getEntryValue(bufferedIdx),
                        "getEntryValue mismatch for key " + keys[i]);
            }

            // Missing keys
            Random rng = new Random(12345);
            Set<Long> keySet = new HashSet<>();
            for (long k : keys) keySet.add(k);
            for (int i = 0; i < 500; i++) {
                long rk = rng.nextLong() & Long.MAX_VALUE;
                if (keySet.contains(rk)) continue;
                assertEquals(directReader.findEntry(rk), bufferedReader.findEntry(rk),
                        "Missing key mismatch for " + rk);
            }
        }
    }

    // ---------------------------------------------------------------
    // Deep trees (height 4+)
    // ---------------------------------------------------------------

    @Test
    void deepTreeHeight4() throws IOException {
        // With page size 512 and entry size 2:
        // leafCapacity = (512-8)/16 = 31
        // internalCapacity = (512-8-8)/16 = 31
        // Height 2: 31 leaves = 31*31 = 961 entries
        // Height 3: 31*32 = 992 leaves = ~30k entries
        // Height 4: need > 32*992 = 31744 leaves = ~984k entries
        // Use 35000 entries with page size 512 to get height 4
        int n = 35_000;
        long[] keys = generateSortedDistinctKeys(n, 44444L);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = i;

        var writer = new PagedBTreeWriter(btreeFile, 512, 2);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], values[i]);
            }
        });

        // Verify with both reader modes
        try (var reader = PagedBTreeReader.direct(btreeFile, 32)) {
            assertEquals(n, reader.numEntries());

            for (int i = 0; i < n; i++) {
                long val = reader.getValue(keys[i]);
                assertEquals(values[i], val,
                        "Wrong value at i=" + i + " key=" + keys[i]);
            }
        }

        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            Random rng = new Random(99);
            for (int trial = 0; trial < 5000; trial++) {
                int idx = rng.nextInt(n);
                assertEquals(values[idx], reader.getValue(keys[idx]));
            }
        }
    }

    // ---------------------------------------------------------------
    // getEntryValue with entrySize=1 (delegates to getEntryKey)
    // ---------------------------------------------------------------

    @Test
    void getEntryValueEntrySize1() throws IOException {
        int n = 200;
        long[] keys = generateSortedDistinctKeys(n, 11111L);

        var writer = new PagedBTreeWriter(btreeFile, 4096, 1);
        writer.write(n, sink -> {
            for (long key : keys) {
                sink.put(key, 0);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(1, reader.entrySize());

            // For entrySize=1, getEntryValue should return the key itself
            for (int i = 0; i < n; i++) {
                long idx = reader.findEntry(keys[i]);
                assertTrue(idx >= 0, "Key not found at i=" + i);
                assertEquals(keys[i], reader.getEntryValue(idx),
                        "getEntryValue should return key for entrySize=1");
            }
        }
    }

    @Test
    void getEntryValueEntrySize1SmallPages() throws IOException {
        // Force multiple leaves with entrySize=1
        int n = 1000;
        long[] keys = generateSortedDistinctKeys(n, 22222L);

        var writer = new PagedBTreeWriter(btreeFile, 512, 1);
        writer.write(n, sink -> {
            for (long key : keys) {
                sink.put(key, 0);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            // Verify across leaf boundaries
            for (int i = 0; i < n; i++) {
                long idx = reader.findEntry(keys[i]);
                assertTrue(idx >= 0);
                assertEquals(keys[i], reader.getEntryValue(idx));
            }
        }
    }

    // ---------------------------------------------------------------
    // Separator key lookups (keys at leaf boundaries)
    // ---------------------------------------------------------------

    @Test
    void lookupSeparatorKeys() throws IOException {
        // With pageSize=512, entrySize=2: leafCapacity = (512-8)/16 = 31
        // Use consecutive keys so we know exactly which keys are separators
        int pageSize = 512;
        int entrySize = 2;
        int leafCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES) / (entrySize * 8);
        int n = leafCapacity * 4; // exactly 4 full leaves

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i + 1, i * 10L);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            // Separator keys are the max key of each leaf (except the last leaf):
            // Leaf 0: keys 1..leafCapacity, separator = leafCapacity
            // Leaf 1: keys leafCapacity+1..2*leafCapacity, separator = 2*leafCapacity
            // etc.
            for (int leaf = 0; leaf < 4; leaf++) {
                int lastKeyInLeaf = (leaf + 1) * leafCapacity;
                int firstKeyInLeaf = leaf * leafCapacity + 1;

                // Last key of each leaf (these ARE the separator keys for non-last leaves)
                assertEquals((lastKeyInLeaf - 1) * 10L, reader.getValue(lastKeyInLeaf),
                        "Failed on last key of leaf " + leaf + " (key=" + lastKeyInLeaf + ")");

                // First key of each leaf
                assertEquals((firstKeyInLeaf - 1) * 10L, reader.getValue(firstKeyInLeaf),
                        "Failed on first key of leaf " + leaf + " (key=" + firstKeyInLeaf + ")");
            }

            // Also check keys adjacent to separators that don't exist
            assertEquals(-1, reader.findEntry(0));
            assertEquals(-1, reader.findEntry(n + 1));
        }

        // Same test with buffered reader
        try (var reader = PagedBTreeReader.buffered(btreeFile)) {
            for (int leaf = 0; leaf < 4; leaf++) {
                int lastKeyInLeaf = (leaf + 1) * leafCapacity;
                int firstKeyInLeaf = leaf * leafCapacity + 1;
                assertEquals((lastKeyInLeaf - 1) * 10L, reader.getValue(lastKeyInLeaf));
                assertEquals((firstKeyInLeaf - 1) * 10L, reader.getValue(firstKeyInLeaf));
            }
        }
    }

    // ---------------------------------------------------------------
    // entrySize > 2 (multi-component entries)
    // ---------------------------------------------------------------

    @Test
    void entrySize3() throws IOException {
        int n = 200;
        long[] keys = generateSortedDistinctKeys(n, 33333L);

        var writer = new PagedBTreeWriter(btreeFile, 4096, 3);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], i * 100L);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(n, reader.numEntries());
            assertEquals(3, reader.entrySize());

            for (int i = 0; i < n; i++) {
                long idx = reader.findEntry(keys[i]);
                assertTrue(idx >= 0, "Key " + keys[i] + " not found");

                // offset 0 = key, offset 1 = value
                long[] result = reader.queryData(new long[]{keys[i]}, 0);
                assertEquals(keys[i], result[0], "queryData offset 0 mismatch");

                result = reader.queryData(new long[]{keys[i]}, 1);
                assertEquals(i * 100L, result[0], "queryData offset 1 mismatch");
            }
        }
    }

    @Test
    void entrySize4SmallPages() throws IOException {
        int n = 500;
        long[] keys = generateSortedDistinctKeys(n, 66666L);

        var writer = new PagedBTreeWriter(btreeFile, 512, 4);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(keys[i], i * 7L);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(n, reader.numEntries());
            assertEquals(4, reader.entrySize());

            for (int i = 0; i < n; i++) {
                assertEquals(i * 7L, reader.getValue(keys[i]),
                        "Wrong value at i=" + i);
            }
        }
    }

    // ---------------------------------------------------------------
    // Partial last leaf with exactly 1 entry
    // ---------------------------------------------------------------

    @Test
    void lastLeafSingleEntry() throws IOException {
        int pageSize = 512;
        int entrySize = 2;
        int leafCapacity = (pageSize - PagedBTreeWriter.PAGE_HEADER_BYTES) / (entrySize * 8);
        // N = exactly one more than fills first leaf -> second leaf has 1 entry
        // Already tested in oneMoreThanLeafCapacity, but let's also test
        // multi-leaf scenario: 3 full leaves + 1 entry
        int n = leafCapacity * 3 + 1;

        var writer = new PagedBTreeWriter(btreeFile, pageSize, entrySize);
        writer.write(n, sink -> {
            for (int i = 0; i < n; i++) {
                sink.put(i + 1, i * 5L);
            }
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            assertEquals(n, reader.numEntries());

            // Specifically verify the lone entry on the last leaf
            assertEquals((n - 1) * 5L, reader.getValue(n),
                    "Last entry (sole occupant of last leaf) not found");

            // And the last entry of the previous full leaf
            assertEquals((leafCapacity * 3 - 1) * 5L, reader.getValue(leafCapacity * 3),
                    "Last entry of last full leaf not found");

            // Verify all entries
            for (int i = 0; i < n; i++) {
                assertEquals(i * 5L, reader.getValue(i + 1),
                        "Wrong value at i=" + i);
            }
        }

        // Also verify via getEntryValue round-trip
        try (var reader = PagedBTreeReader.direct(btreeFile, 8)) {
            long idx = reader.findEntry(n); // last key, sole entry on last leaf
            assertTrue(idx >= 0);
            assertEquals((n - 1) * 5L, reader.getEntryValue(idx));
        }
    }

    // ---------------------------------------------------------------
    // queryData with offset 0 (reads keys back)
    // ---------------------------------------------------------------

    @Test
    void queryDataOffset0ReadsKeys() throws IOException {
        var writer = new PagedBTreeWriter(btreeFile, 4096, 2);
        writer.write(4, sink -> {
            sink.put(10, 100);
            sink.put(20, 200);
            sink.put(30, 300);
            sink.put(40, 400);
        });

        try (var reader = PagedBTreeReader.direct(btreeFile, 4)) {
            long[] queryKeys = {10, 20, 30, 40};
            long[] resultKeys = reader.queryData(queryKeys, 0);
            long[] resultValues = reader.queryData(queryKeys, 1);

            // Offset 0 should return the keys themselves
            assertEquals(10, resultKeys[0]);
            assertEquals(20, resultKeys[1]);
            assertEquals(30, resultKeys[2]);
            assertEquals(40, resultKeys[3]);

            // Offset 1 should return the values
            assertEquals(100, resultValues[0]);
            assertEquals(200, resultValues[1]);
            assertEquals(300, resultValues[2]);
            assertEquals(400, resultValues[3]);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static long[] generateSortedDistinctKeys(int n, long seed) {
        Random rng = new Random(seed);
        Set<Long> set = new TreeSet<>();
        while (set.size() < n) {
            set.add((rng.nextLong() & Long.MAX_VALUE) | 1L);
        }
        return set.stream().mapToLong(Long::longValue).toArray();
    }
}
