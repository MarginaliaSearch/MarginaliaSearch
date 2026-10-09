package nu.marginalia.crawl.retreival;

import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.CrawlerMain;
import nu.marginalia.crawl.DomainStateDb;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlerFeedDiscoveryTest {
    private final HttpFetcher fetcher = mock(HttpFetcher.class);
    private final DomainStateDb stateDb = mock(DomainStateDb.class);
    private final CrawlDelayTimer timer = mock(CrawlDelayTimer.class);
    private final SimpleRobotRules robots = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
    private EdgeUrl rootUrl;
    private EdgeUrl rssUrl;
    private EdgeUrl atomUrl;
    private EdgeUrl articleUrl;
    private CrawlerRetreiver crawler;

    @BeforeEach
    void setUp() throws Exception {
        rootUrl = new EdgeUrl("https://example.com/");
        rssUrl = new EdgeUrl("https://example.com/rss.xml");
        atomUrl = new EdgeUrl("https://example.com/atom.xml");
        articleUrl = new EdgeUrl("https://example.com/article");

        var specs = new CrawlerMain.CrawlSpecRecord("example.com", 10, List.of());
        crawler = new CrawlerRetreiver(fetcher, mock(DomainProber.class), specs, stateDb, mock(WarcRecorder.class));

        when(fetcher.fetchContent(any(), any(), any(), any(), any(), any()))
                .thenReturn(new HttpFetchResult.ResultNone());

        stubHomepage("");
    }

    @Test
    void testRssLink() throws Exception {
        stubHomepage("<link rel='alternate' type='application/rss+xml' href='/rss.xml'>");
        when(fetcher.fetchSitemapUrls(rssUrl.toString(), timer)).thenReturn(List.of(articleUrl));

        var summary = crawler.sniffRootDocument(rootUrl, robots, timer);

        assertEquals(rssUrl.toString(), summary.feedUrl());
        assertEquals(1, crawler.getCrawlFrontier().queueSize());
        assertEquals(articleUrl, crawler.getCrawlFrontier().takeNextUrl());
    }

    @Test
    void testAtomLink() throws Exception {
        stubHomepage("<link rel='alternate' type='application/atom+xml' href='/atom.xml'>");
        when(fetcher.fetchSitemapUrls(atomUrl.toString(), timer)).thenReturn(List.of(articleUrl));

        var summary = crawler.sniffRootDocument(rootUrl, robots, timer);

        assertEquals(atomUrl.toString(), summary.feedUrl());
        assertEquals(1, crawler.getCrawlFrontier().queueSize());
        assertEquals(articleUrl, crawler.getCrawlFrontier().takeNextUrl());
    }

    @Test
    void testAtomPreamble() throws Exception {
        // Put the root element beyond the old 128-character window.
        String comment = "comment ".repeat(30);
        stubContent(atomUrl, "application/atom+xml", """
                <?xml version="1.0"?>
                <!-- %s -->
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Example</title>
                </feed>
                """.formatted(comment));
        when(fetcher.fetchSitemapUrls(atomUrl.toString(), timer)).thenReturn(List.of(articleUrl));

        var summary = crawler.sniffRootDocument(rootUrl, robots, timer);

        assertEquals(atomUrl.toString(), summary.feedUrl());
        assertEquals(1, crawler.getCrawlFrontier().queueSize());
        assertEquals(articleUrl, crawler.getCrawlFrontier().takeNextUrl());
    }

    @Test
    void testSavedFeed() throws Exception {
        EdgeUrl feedUrl = new EdgeUrl("https://example.com/updates.atom");
        var previous = DomainStateDb.SummaryRecord.forSuccess("example.com", feedUrl.toString());
        when(stateDb.getSummary("example.com")).thenReturn(Optional.of(previous));

        stubContent(feedUrl, "application/atom+xml", "<feed xmlns='http://www.w3.org/2005/Atom'/>");
        when(fetcher.fetchSitemapUrls(feedUrl.toString(), timer)).thenReturn(List.of(articleUrl));

        var summary = crawler.sniffRootDocument(rootUrl, robots, timer);

        assertEquals(feedUrl.toString(), summary.feedUrl());
        assertEquals(1, crawler.getCrawlFrontier().queueSize());
        assertEquals(articleUrl, crawler.getCrawlFrontier().takeNextUrl());
        verify(fetcher, never()).fetchContent(eq(rssUrl), any(), any(), any(), any(), any());
    }

    @Test
    void testHtml() throws Exception {
        stubContent(rssUrl, "text/html", """
                <html>
                  <body>
                    <!-- <rss> -->
                    <p>No feed here</p>
                  </body>
                </html>
                """);

        var summary = crawler.sniffRootDocument(rootUrl, robots, timer);

        assertNull(summary.feedUrl());
        assertTrue(crawler.getCrawlFrontier().isEmpty());
        verify(fetcher, never()).fetchSitemapUrls(anyString(), any());
    }

    private void stubHomepage(String head) throws Exception {
        stubContent(rootUrl, "text/html", """
                <html>
                  <head>%s</head>
                  <body></body>
                </html>
                """.formatted(head));
    }

    private void stubContent(EdgeUrl url, String type, String body) throws Exception {
        Header[] headers = { new BasicHeader("Content-Type", type + "; charset=UTF-8") };
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var response = new HttpFetchResult.ResultOk(url.asURI(), 200, headers, "127.0.0.1", bytes);

        when(fetcher.fetchContent(eq(url), any(), any(), any(), any(), any())).thenReturn(response);
    }
}
