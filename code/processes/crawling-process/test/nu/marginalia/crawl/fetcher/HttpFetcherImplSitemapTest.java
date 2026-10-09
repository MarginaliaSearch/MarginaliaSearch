package nu.marginalia.crawl.fetcher;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import nu.marginalia.UserAgent;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpFetcherImplSitemapTest {
    private static WireMockServer server;
    private HttpFetcherImpl fetcher;
    private CrawlDelayTimer timer;

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        server.resetAll();
        fetcher = new HttpFetcherImpl(new UserAgent("test.marginalia.nu", "test.marginalia.nu"));
        timer = mock(CrawlDelayTimer.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        fetcher.close();
    }

    @Test
    void testIndex() {
        stubIndex("/sitemap.xml", url("/pages.xml"));
        stubSitemap("/pages.xml", url("/first"), url("/second"));

        List<String> urls = fetchUrls("/sitemap.xml");

        assertEquals(List.of(url("/first"), url("/second")), urls);
        server.verify(1, getRequestedFor(urlEqualTo("/sitemap.xml")));
        server.verify(1, getRequestedFor(urlEqualTo("/pages.xml")));
        verify(timer, times(2)).waitFetchDelay();
    }

    @Test
    void testCycle() {
        stubIndex("/sitemap.xml", url("/sitemap.xml"), url("/pages.xml"));
        stubSitemap("/pages.xml", url("/article"));

        List<String> urls = fetchUrls("/sitemap.xml");

        assertEquals(List.of(url("/article")), urls);
        server.verify(1, getRequestedFor(urlEqualTo("/sitemap.xml")));
    }

    @Test
    void testDuplicates() {
        stubIndex("/sitemap.xml", url("/pages.xml"), url("/pages.xml"));
        stubSitemap("/pages.xml", url("/article"), url("/article"));

        List<String> urls = fetchUrls("/sitemap.xml");

        assertEquals(List.of(url("/article")), urls);
        server.verify(1, getRequestedFor(urlEqualTo("/pages.xml")));
    }

    @Test
    void testDomain() {
        stubIndex("/sitemap.xml", url("/pages.xml"), "https://other.example/sitemap.xml");
        stubSitemap("/pages.xml", url("/article"), "https://other.example/article");

        List<String> urls = fetchUrls("/sitemap.xml");

        assertEquals(List.of(url("/article")), urls);
        verify(timer, times(2)).waitFetchDelay();
    }

    @Test
    void testRss() {
        stubXml("/rss.xml", """
                <rss version="2.0">
                  <channel>
                    <item>
                      <link>%s</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(url("/article")));

        List<String> urls = fetchUrls("/rss.xml");

        assertEquals(List.of(url("/article")), urls);
    }

    @Test
    void testAtom() {
        stubXml("/atom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <link rel="alternate" href="%s"/>
                  </entry>
                  <entry>
                    <link href="/second"/>
                  </entry>
                </feed>
                """.formatted(url("/first")));

        List<String> urls = fetchUrls("/atom.xml");

        assertEquals(List.of(url("/first"), url("/second")), urls);
    }

    @Test
    void testAtomFiltering() {
        stubXml("/atom.xml", """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <link rel="self" href="/atom.xml"/>
                  <entry>
                    <link rel="self" href="/entry.xml"/>
                    <link rel="enclosure" href="/audio.mp3"/>
                  </entry>
                  <entry>
                    <link href="https://other.example/article"/>
                  </entry>
                  <entry>
                    <link href=""/>
                  </entry>
                </feed>
                """);

        List<String> urls = fetchUrls("/atom.xml");

        assertTrue(urls.isEmpty());
    }

    @Test
    void testMapLimit() {
        List<String> sitemapUrls = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String path = "/sitemap-" + i + ".xml";
            stubSitemap(path, url("/page-" + i));
            sitemapUrls.add(url(path));
        }
        stubIndex("/sitemap.xml", sitemapUrls.toArray(String[]::new));

        List<String> urls = fetchUrls("/sitemap.xml");

        // The index counts toward the ten-document limit, leaving nine sitemaps.
        assertEquals(9, urls.size());
        assertEquals(10, server.getAllServeEvents().size());
        verify(timer, times(10)).waitFetchDelay();
    }

    @Test
    void testUrlLimit() {
        String[] pageUrls = new String[20_005];
        for (int i = 0; i < pageUrls.length; i++) {
            pageUrls[i] = url("/page-" + i);
        }
        stubSitemap("/sitemap.xml", pageUrls);

        List<String> urls = fetchUrls("/sitemap.xml");

        assertEquals(20_000, urls.size());
        assertEquals(url("/page-19999"), urls.getLast());
    }

    private void stubIndex(String path, String... sitemapUrls) {
        StringBuilder body = new StringBuilder("""
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        for (String sitemapUrl : sitemapUrls) {
            body.append("<sitemap><loc>").append(sitemapUrl).append("</loc></sitemap>\n");
        }
        body.append("</sitemapindex>");

        stubXml(path, body.toString());
    }

    private void stubSitemap(String path, String... pageUrls) {
        StringBuilder body = new StringBuilder("""
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        for (String pageUrl : pageUrls) {
            body.append("<url><loc>").append(pageUrl).append("</loc></url>\n");
        }
        body.append("</urlset>");

        stubXml(path, body.toString());
    }

    private void stubXml(String path, String body) {
        server.stubFor(get(urlEqualTo(path)).willReturn(okXml(body)));
    }

    private String url(String path) {
        return server.baseUrl() + path;
    }

    private List<String> fetchUrls(String path) {
        List<EdgeUrl> urls = fetcher.fetchSitemapUrls(url(path), timer);
        return urls.stream().map(EdgeUrl::toString).toList();
    }
}
