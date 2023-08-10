package nu.marginalia.crawl.retreival;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainCrawlFrontierTest {

    @Test
    public void testVisited() throws URISyntaxException {
        var dcf = new DomainCrawlFrontier(new EdgeDomain("example.com"), Set.of(), 100);

        assertTrue(dcf.addVisited(new EdgeUrl("https://example.com")));
        assertTrue(dcf.isVisited(new EdgeUrl("https://example.com")));
        assertFalse(dcf.addVisited(new EdgeUrl("https://example.com")));
    }

    @Test
    public void testKnown() throws URISyntaxException {
        var dcf = new DomainCrawlFrontier(new EdgeDomain("example.com"), Set.of(), 100);

        assertTrue(dcf.addKnown(new EdgeUrl("https://example.com")));
        assertFalse(dcf.addKnown(new EdgeUrl("https://example.com/")));
        assertTrue(dcf.addKnown(new EdgeUrl("https://example.com/index.html")));
        assertFalse(dcf.addKnown(new EdgeUrl("https://example.com")));
    }
}