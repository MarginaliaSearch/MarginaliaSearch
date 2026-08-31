package nu.marginalia.array.pool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UncachedBufferPoolTest {
    private static final int PAGE_SIZE = 8192;
    private static final int PAGE_COUNT = 32;

    Path file;

    @BeforeEach
    void setUp() throws IOException {
        file = Files.createTempFile(UncachedBufferPoolTest.class.getSimpleName(), ".dat");

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
    void testReads() throws Exception {
        try (var pool = new UncachedBufferPool(file, PAGE_SIZE, 4)) {
            for (int i = 0; i < PAGE_COUNT; i++) {
                try (var page = pool.get((long) i * PAGE_SIZE)) {
                    assertEquals(i, page.getLong(0));
                }
            }
            assertEquals(PAGE_COUNT, pool.getDiskReadCount());
        }
    }

    @Test
    void testPageRecycling() throws Exception {
        // With a single page in the pool, every get() must reuse the page
        // released by the previous close()
        try (var pool = new UncachedBufferPool(file, PAGE_SIZE, 1)) {
            for (int i = 0; i < PAGE_COUNT; i++) {
                try (var page = pool.get((long) i * PAGE_SIZE)) {
                    assertEquals(i, page.getLong(0));
                }
            }
        }
    }

    @Test
    void testReadAhead() throws Exception {
        try (var pool = new UncachedBufferPool(file, PAGE_SIZE, 4)) {
            try (var page = pool.get(3L * PAGE_SIZE, 8)) {
                assertEquals(3, page.getLong(0));
            }
            assertEquals(1, pool.getDiskReadCount());
            assertEquals(8, pool.getReadAheadCount());
        }
    }

    @Test
    void testReadAhead__endOfFile() throws Exception {
        try (var pool = new UncachedBufferPool(file, PAGE_SIZE, 4)) {
            try (var page = pool.get(30L * PAGE_SIZE, 8)) {
                assertEquals(30, page.getLong(0));
            }
            assertEquals(1, pool.getReadAheadCount());

            try (var page = pool.get(31L * PAGE_SIZE, 8)) {
                assertEquals(31, page.getLong(0));
            }
            assertEquals(1, pool.getReadAheadCount());
        }
    }

    @Test
    void testOutOfBounds() throws Exception {
        try (var pool = new UncachedBufferPool(file, PAGE_SIZE, 4)) {
            assertThrows(IllegalArgumentException.class, () -> pool.get((long) PAGE_COUNT * PAGE_SIZE));
        }
    }
}
