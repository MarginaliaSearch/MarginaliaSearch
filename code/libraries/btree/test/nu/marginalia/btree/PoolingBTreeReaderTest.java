package nu.marginalia.btree;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class PoolingBTreeReaderTest {
    private static Path testFile;
    static BTreeContext ctx = new BTreeContext(4, 1, BTreeBlockSize.BS_512);
    static BTreeContext ctxKV = new BTreeContext(4, 2, BTreeBlockSize.BS_512);

    @BeforeAll
    public static void setUpAll() throws IOException {
        testFile = Files.createTempFile(PoolingBTreeReaderTest.class.getSimpleName(), "dat");
    }

    @BeforeEach
    public void setUp() {
    }


    @Test
    public void testReadSmallTree() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 20, slice -> {
                for (int i = 0; i < 20; i++) {
                    slice.set(i, 3*i);
                }
            });

        }

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            LongArray dest = LongArrayFactory.onHeapShared(64);

            int n = reader.readData(dest, 64, 0);
            System.out.println(n);
            for (int i = 0; i < n; i++) {
                System.out.println(dest.get(i));
            }
        }
    }

    @Test
    public void testReadSmallTreeKV() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctxKV);
            btw.write(256, 20, slice -> {
                for (int i = 0; i < 20; i++) {
                    slice.set(2*i, 3*i);
                    slice.set(2*i+1, -3*i);
                }
            });

        }

        try (var indexPool = new BufferPool(testFile, 8 * ctxKV.pageSize(), 32);
             var dataPool = new BufferPool(testFile, 8 * ctxKV.pageSize() * ctxKV.entrySize, 32);
        ) {
            var reader = new PoolingBTreeReader(indexPool, dataPool, ctxKV, 256);

            LongArray dest = LongArrayFactory.onHeapShared(64);

            long[] expected = new long[40];
            long[] actual = new long[40];

            int n = reader.readData(dest, 64, 0);

            System.out.println(n);

            for (int i = 0; i < 20; i++) {
                expected[2*i] = 3*i;
                expected[2*i + 1] = -3*i;
            }

            for (int i = 0; i < n; i++) {
                actual[i] = dest.get(i);
                System.out.println(dest.get(i));
            }

            System.out.println(Arrays.toString(expected));
            System.out.println(Arrays.toString(actual));

            Assertions.assertArrayEquals(expected, actual);
        }
    }

    @Test
    public void testRetainSmall() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 20, slice -> {
                for (int i = 0; i < 20; i++) {
                    slice.set(i, 3*i);
                }
            });

        }

        long[] filterData = new long[60];
        for (int i = 0; i < 60; i++) {
            filterData[i] = i;
        }
        LongQueryBuffer lqb = new LongQueryBuffer(filterData, 60);

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            reader.retainEntries(lqb);
            lqb.finalizeFiltering();
            long[] expected = new long[20];
            for (int i = 0; i < 20; i++) {
                expected[i] = 3*i;
            }
            assertArrayEquals(expected, lqb.copyData());
        }
    }

    @Test
    public void testRejectSmall() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 20, slice -> {
                for (int i = 0; i < 20; i++) {
                    slice.set(i, 3*i);
                }
            });

        }

        long[] filterData = new long[60];
        for (int i = 0; i < 60; i++) {
            filterData[i] = i;
        }
        LongQueryBuffer lqb = new LongQueryBuffer(filterData, 60);

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            reader.rejectEntries(lqb);
            lqb.finalizeFiltering();
            long[] expected = LongStream.range(0, 60).filter(lv->(lv%3)!=0).toArray();
            System.out.println(Arrays.toString(lqb.copyData()));
            System.out.println(Arrays.toString(expected));
            assertArrayEquals(expected, lqb.copyData());
        }
    }

    @Test
    public void findInSmallTree() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 20, slice -> {
                for (int i = 0; i < 20; i++) {
                    slice.set(i, 3*i);
                }
            });
        }

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            for (int i = 0; i < 60; i++) {
                if ((i % 3) == 0) {
                    assertTrue(reader.containsEntry(i), Integer.toString(i));
                }
                else {
                    assertFalse(reader.containsEntry(i), Integer.toString(i));
                }
            }
        }
    }

    @Test
    public void testReadLargeTree() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 1024, slice -> {
                for (int i = 0; i < 1024; i++) {
                    slice.set(i, 3*i);
                }
            });

        }

        System.out.println(ctx.pageSize());

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            for (int i = 0; i < 10; i++) {
                System.out.println(reader.containsEntry(0));
            }

        }
    }

    @Test
    public void testGetData() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctxKV);
            btw.write(256, 1024, slice -> {
                for (int i = 0; i < 1024; i++) {
                    slice.set(2*i, i);
                    slice.set(2*i+1, -i);
                }
            });
        }

        System.out.println(ctxKV.pageSize()*8);

        try (var indexPool = new BufferPool(testFile, 8 * ctxKV.pageSize(), 32);
             var dataPool = new BufferPool(testFile, 8 * ctxKV.pageSize() * ctxKV.entrySize, 32);
        ) {
            var reader = new PoolingBTreeReader(indexPool, dataPool, ctxKV, 256);

            long[] keys = new long[] { 3, 9, 27, 512, 1029 };
            long[] ret = reader.queryData(keys, 1);
            System.out.println(Arrays.toString(ret));
        }
    }

    @Test
    public void testGetDataTiny() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctxKV);
            btw.write(256, 1, slice -> {
                slice.set(0, 4);
                slice.set(1, 5);
            });
        }

        try (var indexPool = new BufferPool(testFile, 8 * ctxKV.pageSize(), 32);
             var dataPool = new BufferPool(testFile, 8 * ctxKV.pageSize() * ctxKV.entrySize, 32);
        ) {
            var reader = new PoolingBTreeReader(indexPool, dataPool, ctxKV, 256);

            long[] keys = new long[] { 3,4,5 };
            long[] ret = reader.queryData(keys, 1);
            long[] expected = new long[] { 0, 5, 0 };
            System.out.println(Arrays.toString(ret));
            Assertions.assertArrayEquals(expected, ret);
        }
    }
    @Test
    public void testRetainLargeTree() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 1024, slice -> {
                for (int i = 0; i < 1024; i++) {
                    slice.set(i, 3*i);
                }
            });

        }

        long[] filterData = new long[600];
        for (int i = 0; i < 600; i++) {
            filterData[i] = 30 + i;
        }

        LongQueryBuffer lqb = new LongQueryBuffer(filterData, 600);

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            reader.retainEntries(lqb);
            lqb.finalizeFiltering();
            long[] expected = new long[200];
            for (int i = 0; i < 200; i++) {
                expected[i] = 30 + 3*i;
            }
            assertArrayEquals(expected, lqb.copyData());
        }
    }

    @Test
    public void testRetainLargeTreeKV() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctxKV);
            btw.write(64, 1024, slice -> {
                for (int i = 0; i < 1024; i++) {
                    slice.set(2*i, 3*i);
                    slice.set(2*i+1, -3*i);
                }
            });

        }

        long[] filterData = new long[600];
        for (int i = 0; i < 600; i++) {
            filterData[i] = 30 + i;
        }

        LongQueryBuffer lqb = new LongQueryBuffer(filterData, 600);

        try (var indexPool = new BufferPool(testFile, 8 * ctxKV.pageSize(), 32);
             var dataPool = new BufferPool(testFile, 8 * ctxKV.pageSize() * ctxKV.entrySize, 32);
        ) {
            var reader = new PoolingBTreeReader(indexPool, dataPool, ctxKV, 64);

            reader.retainEntries(lqb);
            lqb.finalizeFiltering();
            long[] expected = new long[200];
            for (int i = 0; i < 200; i++) {
                expected[i] = 30 + 3*i;
            }
            System.out.println(Arrays.toString(expected));
            System.out.println(Arrays.toString(lqb.copyData()));
            assertArrayEquals(expected, lqb.copyData());
        }
    }

    @Test
    public void testRejectLargeTree() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 1024, slice -> {
                for (int i = 0; i < 1024; i++) {
                    slice.set(i, 3*i);
                }
            });

        }

        long[] filterData = new long[600];
        for (int i = 0; i < 600; i++) {
            filterData[i] = 30 + i;
        }

        LongQueryBuffer lqb = new LongQueryBuffer(filterData, 600);

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            reader.rejectEntries(lqb);
            lqb.finalizeFiltering();
            long[] expected = LongStream.range(30, 630).filter(lv->(lv%3)!=0).toArray();
            System.out.println(Arrays.toString(lqb.copyData()));
            System.out.println(Arrays.toString(expected));
            assertArrayEquals(expected, lqb.copyData());
        }
    }

    @Test
    public void findInLargeTree() throws Exception {
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            BTreeWriter btw = new BTreeWriter(array, ctx);
            btw.write(64, 1024, slice -> {
                for (int i = 0; i < 1024; i++) {
                    slice.set(i, 3*i);
                }
            });
        }

        try (var pool = new BufferPool(testFile, 8 * ctx.pageSize(), 32)) {
            var reader = new PoolingBTreeReader(pool, pool, ctx, 64);

            for (int i = 0; i < 1024; i++) {
                if ((i % 3) == 0) {
                    assertTrue(reader.containsEntry(i), Integer.toString(i));
                }
                else {
                    assertFalse(reader.containsEntry(i), Integer.toString(i));
                }
            }
        }
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
    }


    @AfterAll
    public static void tearDownAll() throws IOException {
        Files.delete(testFile);
    }
}