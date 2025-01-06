package nu.marginalia.rss.svc;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

public class TestXmlSanitization {

    @Test
    public void testPreservedEntities() {
        Assertions.assertEquals("&amp;", FeedFetcherService.sanitizeEntities("&amp;"));
        Assertions.assertEquals("&lt;", FeedFetcherService.sanitizeEntities("&lt;"));
        Assertions.assertEquals("&gt;", FeedFetcherService.sanitizeEntities("&gt;"));
        Assertions.assertEquals("&apos;", FeedFetcherService.sanitizeEntities("&apos;"));
    }

    @Test
    public void testNlnetTitleTag() {
        // The NLnet atom feed puts HTML tags in the entry/title tags, which breaks the vanilla RssReader code

        // Verify we're able to consume and strip out the HTML tags
        RssReader r = new RssReader();

        List<Item> items = r.read(ClassLoader.getSystemResourceAsStream("nlnet.atom")).toList();

        Assertions.assertEquals(1, items.size());
        for (var item : items) {
            Assertions.assertEquals(Optional.of("50 Free and Open Source Projects Selected for NGI Zero grants"), item.getTitle());
        }
    }

    @Test
    public void testStrayAmpersand() {
        Assertions.assertEquals("Bed &amp; Breakfast", FeedFetcherService.sanitizeEntities("Bed & Breakfast"));
    }

    @Test
    public void testTranslatedHtmlEntity() {
        Assertions.assertEquals("Foo -- Bar", FeedFetcherService.sanitizeEntities("Foo &mdash; Bar"));
    }

    @Test
    public void testTranslatedHtmlEntityQuot() {
        Assertions.assertEquals("\"Bob\"", FeedFetcherService.sanitizeEntities("&quot;Bob&quot;"));
    }
}
