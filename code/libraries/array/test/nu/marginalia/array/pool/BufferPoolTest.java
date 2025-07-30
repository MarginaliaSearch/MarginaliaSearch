package nu.marginalia.array.pool;

import nu.marginalia.array.LongArrayFactory;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BufferPoolTest {
    private static Path testFile;
    private BufferPool pool;
    @BeforeAll
    public static void setUpAll() throws IOException {
        testFile = Files.createTempFile(BufferPoolTest.class.getSimpleName(), "dat");
        try (var array = LongArrayFactory.mmapForWritingConfined(testFile, 65536)) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, i);
            }
        }
    }

    @BeforeEach
    public void setUp() {
        pool = new BufferPool(testFile, 64, 16);
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        pool.close();
    }


    @AfterAll
    public static void tearDownAll() throws IOException {
        Files.delete(testFile);
    }

    @Test
    public void testCacheRetain() {
        var buffer = pool.get(64, BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.NONE);
        var buffer2 = pool.get(64, BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.NONE);

        assertSame(buffer, buffer2);
        assertEquals(2, buffer.pinCount().get());

        buffer2.close();
        buffer.close();

        var buffer3 = pool.get(64, BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.NONE);
        assertSame(buffer2, buffer3);
        assertEquals(1, pool.diskReadCount.get());
    }

    @Test
    public void testCacheDrop() {
        var buffer = pool.get(64, BufferEvictionPolicy.READ_ONCE, BufferReadaheadPolicy.NONE);
        var buffer2 = pool.get(64, BufferEvictionPolicy.READ_ONCE, BufferReadaheadPolicy.NONE);

        assertSame(buffer, buffer2);
        assertEquals(2, buffer.pinCount().get());

        buffer2.close();
        buffer.close();

        var buffer3 = pool.get(64, BufferEvictionPolicy.READ_ONCE, BufferReadaheadPolicy.NONE);
        assertEquals(2, pool.diskReadCount.get());
        buffer3.close();
    }

    @Test
    public void testReadahead() throws InterruptedException {
        var buffer = pool.get(64, BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.SMALL);
        Thread.sleep(1000);
        var buffer2 = pool.getExistingBufferForReading(128);
        assertNotNull(buffer2);
        assertEquals(1, pool.diskReadCount.get());
        assertEquals(1, pool.readaheadFetchCount.get());
    }

    @Test
    public void testReadaheadAggro() throws InterruptedException {
        var buffer = pool.get(64, BufferEvictionPolicy.CACHE, BufferReadaheadPolicy.AGGRESSIVE);
        Thread.sleep(1000);
        for (int i = 0; i < 3; i++) {
            var buffer2 = pool.getExistingBufferForReading(64 + 64L*i);
            assertNotNull(buffer2);
        }
    }
}