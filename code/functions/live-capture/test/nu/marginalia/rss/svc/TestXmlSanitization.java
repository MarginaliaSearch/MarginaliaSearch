package nu.marginalia.rss.svc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestXmlSanitization {

    @Test
    public void testPreservedEntities() {
        Assertions.assertEquals("&amp;", FeedFetcherService.sanitizeEntities("&amp;"));
        Assertions.assertEquals("&lt;", FeedFetcherService.sanitizeEntities("&lt;"));
        Assertions.assertEquals("&gt;", FeedFetcherService.sanitizeEntities("&gt;"));
        Assertions.assertEquals("&quot;", FeedFetcherService.sanitizeEntities("&quot;"));
        Assertions.assertEquals("&apos;", FeedFetcherService.sanitizeEntities("&apos;"));
    }

    @Test
    public void testStrayAmpersand() {
        Assertions.assertEquals("Bed &amp; Breakfast", FeedFetcherService.sanitizeEntities("Bed & Breakfast"));
    }

    @Test
    public void testTranslatedHtmlEntity() {
        Assertions.assertEquals("Foo -- Bar", FeedFetcherService.sanitizeEntities("Foo &mdash; Bar"));
    }
}
