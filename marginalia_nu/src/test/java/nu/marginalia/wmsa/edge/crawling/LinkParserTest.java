package nu.marginalia.wmsa.edge.crawling;

import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class LinkParserTest {

    private String parseLink(String href, String base) throws URISyntaxException {
        var url = new EdgeUrl("http://www.marginalia.nu/" + base);
        var domain = url.domain;
        var parser = new LinkParser();
        var stuff = Jsoup.parseBodyFragment("<a href='"+href+"''>test</a>");
        var lnk = parser.parseLink(
                url,
                stuff.getElementsByTag("a").get(0));

        if (lnk.isEmpty()) {
            return null;
        }

        return lnk.get().toString();
    }

    @Test
    void testRenormalization() throws URISyntaxException {
        assertEquals("http://www.marginalia.nu/test", parseLink("http://www.marginalia.nu/../test", "/"));
    }

    @Test
    void testRenormalization2() {
        assertTrue("http:".matches("^[a-zA-Z]+:"));
        assertFalse("/foo".matches("^[a-zA-Z]+:"));
    }


    @Test
    void testAnchor() throws URISyntaxException {
        assertNull(parseLink("#test", "/"));
    }
    @Test
    void testRelative() throws URISyntaxException {
        assertEquals("http://www.marginalia.nu/test", parseLink("../test", "/"));
        assertEquals("http://www.marginalia.nu/test", parseLink("test", "/"));
        assertEquals("http://www.marginalia.nu/foo/test", parseLink("test", "/foo/index.html"));
        assertEquals("http://www.marginalia.nu/test", parseLink("../test", "/foo/index.html"));
        assertEquals("http://www.marginalia.nu/test", parseLink("/test", "/foo/index.html"));
    }
}