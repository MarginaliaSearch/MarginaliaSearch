package nu.marginalia.functions.index.results;

import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.model.id.UrlIdCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IndexResultDomainDeduplicatorTest {

    @AfterEach
    public void clear() {
        IndexResultDomainDeduplicator.clearCachedObjects();
    }

    @Test
    public void testDeduplicator() {

        IndexResultDomainDeduplicator deduplicator = new IndexResultDomainDeduplicator(3);

        assertTrue(deduplicator.test(forId(3, 0)));
        assertTrue(deduplicator.test(forId(3, 1)));
        assertTrue(deduplicator.test(forId(3, 2)));
        assertFalse(deduplicator.test(forId(3, 3)));
        assertFalse(deduplicator.test(forId(3, 4)));

        assertEquals(5, deduplicator.getCount(forId(3, 3)));
    }

    SearchResultItem forId(int domain, int ordinal) {
        return new SearchResultItem(UrlIdCodec.encodeId(domain, ordinal), 4);
    }

}