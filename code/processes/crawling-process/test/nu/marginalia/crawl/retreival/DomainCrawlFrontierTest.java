package nu.marginalia.crawl.retreival;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainCrawlFrontierTest {

    @Test
    public void testVisited() throws URISyntaxException {
        var dcf = new DomainCrawlFrontier(new EdgeDomain("example.com"), Set.of(), 100);

        assertFalse(dcf.isVisited(new EdgeUrl("https://example.com")));
        assertTrue(dcf.addVisited(new EdgeUrl("https://example.com")));
        assertTrue(dcf.isVisited(new EdgeUrl("https://example.com")));
        assertFalse(dcf.addVisited(new EdgeUrl("https://example.com")));
    }

    @Test
    public void testKnown() throws URISyntaxException {
        var dcf = new DomainCrawlFrontier(new EdgeDomain("example.com"), Set.of(), 100);

        assertFalse(dcf.isKnown(new EdgeUrl("https://example.com/")));
        assertTrue(dcf.addKnown(new EdgeUrl("https://example.com")));
        assertTrue(dcf.isKnown(new EdgeUrl("https://example.com/")));
        assertFalse(dcf.addKnown(new EdgeUrl("https://example.com/")));
        assertTrue(dcf.addKnown(new EdgeUrl("https://example.com/index.html")));
        assertFalse(dcf.addKnown(new EdgeUrl("https://example.com")));
    }

    @Test
    public void testSchemaRewriting__http_to_https() throws URISyntaxException {
        var dcf = new DomainCrawlFrontier(new EdgeDomain("www.example.com"), Set.of(), 100);

        dcf.setSupportsHttps(true);

        dcf.addToQueue(new EdgeUrl("https://www.example.com"));
        dcf.addToQueue(new EdgeUrl("http://www.example.com/cat.png"));

        Assertions.assertEquals(new EdgeUrl("https://www.example.com/"), dcf.takeNextUrl());
        Assertions.assertEquals(new EdgeUrl("https://www.example.com/cat.png"), dcf.takeNextUrl());
    }

    @Test
    public void testSchemaRewriting__https_to_http() throws URISyntaxException {
        var dcf = new DomainCrawlFrontier(new EdgeDomain("www.example.com"), Set.of(), 100);

        dcf.setSupportsHttps(false);

        dcf.addToQueue(new EdgeUrl("https://www.example.com"));
        dcf.addToQueue(new EdgeUrl("http://www.example.com/cat.png"));

        Assertions.assertEquals(new EdgeUrl("http://www.example.com/"), dcf.takeNextUrl());
        Assertions.assertEquals(new EdgeUrl("http://www.example.com/cat.png"), dcf.takeNextUrl());
    }
}