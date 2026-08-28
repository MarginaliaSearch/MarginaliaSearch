package nu.marginalia.array.pool;

import nu.marginalia.ffi.IoUring;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BufferPoolTest {
    private static final int PAGE_SIZE = 8192;
    private static final int PAGE_COUNT = 32;

    Path file;

    @BeforeEach
    void setUp() throws IOException {
        file = Files.createTempFile(BufferPoolTest.class.getSimpleName(), ".dat");

        // Each page starts with its own index, so that reads can be verified
        ByteBuffer contents = ByteBuffer.allocate(PAGE_SIZE * PAGE_COUNT).order(ByteOrder.nativeOrder());
        for (int i = 0; i < PAGE_COUNT; i++) {
            contents.putLong(i * PAGE_SIZE, i);
        }
        Files.write(file, contents.array());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(file);
    }

    @Test
    void testReadAhead() throws Exception {
        Assumptions.assumeTrue(IoUring.isAvailable);

        try (var pool = new BufferPool(file, PAGE_SIZE, 64)) {
            try (var page = pool.get(3L * PAGE_SIZE, 8)) {
                assertEquals(3, page.getLong(0));
            }
            assertEquals(9, pool.getDiskReadCount());
            assertEquals(8, pool.getReadAheadCount());

            for (int i = 4; i < 12; i++) {
                try (var page = pool.get((long) i * PAGE_SIZE)) {
                    assertEquals(i, page.getLong(0));
                }
            }
            assertEquals(9, pool.getDiskReadCount());
            assertEquals(8, pool.getCacheReadCount());
        }
    }

    @Test
    void testReadAhead__endOfFile() throws Exception {
        Assumptions.assumeTrue(IoUring.isAvailable);

        try (var pool = new BufferPool(file, PAGE_SIZE, 64)) {
            try (var page = pool.get(30L * PAGE_SIZE, 8)) {
                assertEquals(30, page.getLong(0));
            }
            assertEquals(2, pool.getDiskReadCount());

            try (var page = pool.get(31L * PAGE_SIZE)) {
                assertEquals(31, page.getLong(0));
            }
            assertEquals(2, pool.getDiskReadCount());
        }
    }

    @Test
    void testReadahead__alreadyPopulated() throws Exception {
        Assumptions.assumeTrue(IoUring.isAvailable);

        try (var pool = new BufferPool(file, PAGE_SIZE, 64)) {
            try (var page = pool.get(6L * PAGE_SIZE)) {
                assertEquals(6, page.getLong(0));
            }
            try (var page = pool.get(4L * PAGE_SIZE, 4)) {
                assertEquals(4, page.getLong(0));
            }
            assertEquals(5, pool.getDiskReadCount());

            for (int i = 4; i <= 8; i++) {
                try (var page = pool.get((long) i * PAGE_SIZE)) {
                    assertEquals(i, page.getLong(0));
                }
            }
            assertEquals(5, pool.getDiskReadCount());
        }
    }

    @Test
    void testReadAhead__pressure() throws Exception {
        Assumptions.assumeTrue(IoUring.isAvailable);

        try (var pool = new BufferPool(file, PAGE_SIZE, PAGE_COUNT)) {
            // Hold most of the pool, leaving fewer free pages than read-ahead is allowed to take
            List<MemoryPage> held = new ArrayList<>();
            for (int i = 0; i < PAGE_COUNT - 2; i++) {
                held.add(pool.get((long) i * PAGE_SIZE));
            }
            long readsBefore = pool.getDiskReadCount();

            try (var page = pool.get((long) (PAGE_COUNT - 2) * PAGE_SIZE, 8)) {
                assertEquals(PAGE_COUNT - 2, page.getLong(0));
            }
            assertEquals(readsBefore + 1, pool.getDiskReadCount());
            assertEquals(0, pool.getReadAheadCount());
            assertEquals(1, pool.getReadAheadSkippedCount());

            held.forEach(MemoryPage::close);
        }
    }

    @Test
    void testSmallPool() throws Exception {
        Assumptions.assumeTrue(IoUring.isAvailable);

        // A pool of eight pages can spare one for read-ahead
        try (var pool = new BufferPool(file, PAGE_SIZE, 8)) {
            try (var page = pool.get(0, 8)) {
                assertEquals(0, page.getLong(0));
            }
            assertEquals(2, pool.getDiskReadCount());
            assertEquals(1, pool.getReadAheadCount());
        }
    }
}
