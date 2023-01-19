package nu.marginalia.wmsa.edge.crawling;

import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class LinkParserTest {

    private String parseLink(String href, String relBase) throws URISyntaxException {
        var url = new EdgeUrl("http://www.marginalia.nu/" + relBase);
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
        assertEquals("http://search.marginalia.nu/", parseLink("//search.marginalia.nu", "/"));
        assertEquals("http://www.marginalia.nu/test", parseLink("../test", "/"));
        assertEquals("http://www.marginalia.nu/test", parseLink("test", "/"));
        assertEquals("http://www.marginalia.nu/foo/test", parseLink("test", "/foo/index.html"));
        assertEquals("http://www.marginalia.nu/test", parseLink("../test", "/foo/index.html"));
        assertEquals("http://www.marginalia.nu/test", parseLink("/test", "/foo/index.html"));
    }

    private EdgeUrl getBaseUrl(String href, EdgeUrl documentUrl) {
        LinkParser lp = new LinkParser();

        return lp.getBaseLink(Jsoup.parse("<base href=\"" + href + "\" />"), documentUrl);
    }

    @Test
    public void getBaseUrlTest() throws URISyntaxException {
        assertEquals(new EdgeUrl("https://www.marginalia.nu/base"),
                getBaseUrl("/base", new EdgeUrl("https://www.marginalia.nu/test/foo.bar")));

        assertEquals(new EdgeUrl("https://memex.marginalia.nu/base"),
                getBaseUrl("https://memex.marginalia.nu/base", new EdgeUrl("https://www.marginalia.nu/test/foo.bar")));

        assertEquals(new EdgeUrl("https://www.marginalia.nu/test/base"),
                getBaseUrl("base", new EdgeUrl("https://www.marginalia.nu/test/foo.bar")));
    }

    @Test
    public void testParseBadBaseLink() throws URISyntaxException {
        LinkParser lp = new LinkParser();
        var url = new EdgeUrl("https://memex.marginalia.nu/");

        assertEquals(url, lp.getBaseLink(Jsoup.parse("<base href/>"), url));
        assertEquals(url, lp.getBaseLink(Jsoup.parse("<base target=\"foo\"/>"), url));
        assertEquals(url, lp.getBaseLink(Jsoup.parse("<base href=\"http://\"/>"), url));
    }
}