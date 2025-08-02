package nu.marginalia.array.pool;

import nu.marginalia.array.page.UnsafeLongArrayBuffer;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

class PoolLruTest {
    @Test
    public void testReclamation() {
        UnsafeLongArrayBuffer[] pages = new UnsafeLongArrayBuffer[256];

        for (int i = 0; i < pages.length; i++) {
            pages[i] = new UnsafeLongArrayBuffer(MemorySegment.NULL, i);
        }

        PoolLru lru = new PoolLru(pages);

        for (int t = 0; t < 32; t++) {
            Thread.ofPlatform().start(() -> {
                for (long a = 0; ; a++) {
                    var page = lru.getFree();
                    page.pageAddress(a++);
                    lru.register(page);
                }
            });
        }
        for (;;);
    }
}