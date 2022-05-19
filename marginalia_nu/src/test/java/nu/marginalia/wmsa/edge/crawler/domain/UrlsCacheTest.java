package nu.marginalia.wmsa.edge.crawler.domain;

import nu.marginalia.wmsa.edge.model.WideHashable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlsCacheTest {

    static class TestBox implements WideHashable {
        public final long value;

        TestBox(long value) {
            this.value = value;
        }

        @Override
        public long wideHash() {
            return value;
        }
    }

    @Test
    void testCacheEviction() {
        var cache = new UrlsCache<TestBox>(5);
        cache.add(new TestBox(0));
        hasValues(cache, 0L);
        cache.add(new TestBox(1));
        hasValues(cache, 0L, 1L);
        cache.add(new TestBox(2));
        hasValues(cache, 0L, 1L, 2L);
        cache.add(new TestBox(3));
        hasValues(cache, 0L, 1L, 2L, 3L);
        cache.add(new TestBox(4));
        hasValues(cache, 0L, 1L, 2L, 3L, 4L);
        cache.add(new TestBox(5));
        hasValues(cache, 1L, 2L, 3L, 4L, 5L);
        hasntValues(cache, 0L);
        cache.add(new TestBox(6));
        hasValues(cache, 2L, 3L, 4L, 5L, 6L);
        hasntValues(cache, 0L, 1L);
        cache.add(new TestBox(7));
        hasValues(cache, 3L, 4L, 5L, 6L);
        hasntValues(cache, 0L, 1L, 2L);
    }

    public void hasValues(UrlsCache<TestBox> box, long... values) {
        for (long v : values) {
            assertTrue(box.contains(new TestBox(v)), () -> "Testing if cache contains " + v);
        }
    }
    public void hasntValues(UrlsCache<TestBox> box, long... values) {
        for (long v : values) {
            assertFalse(box.contains(new TestBox(v)), () -> "Testing if cache misses " + v);
        }
    }
}