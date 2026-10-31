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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrawlerHumanJsonTest {
    private final HttpFetcher fetcher = mock(HttpFetcher.class);
    private final WarcRecorder recorder = mock(WarcRecorder.class);
    private final CrawlDelayTimer timer = mock(CrawlDelayTimer.class);
    private final SimpleRobotRules robots = mock(SimpleRobotRules.class);

    private EdgeUrl rootUrl;
    private CrawlerRetreiver crawler;

    @BeforeEach
    void setUp() throws Exception {
        rootUrl = new EdgeUrl("https://example.com/");
        crawler = new CrawlerRetreiver(fetcher, mock(DomainProber.class),
                new CrawlerMain.CrawlSpecRecord("example.com", 10, List.of()),
                mock(DomainStateDb.class), recorder);

        when(robots.isAllowed(anyString())).thenReturn(true);
        when(fetcher.fetchContent(any(), any(), any(), any(), any(), any()))
                .thenReturn(new HttpFetchResult.ResultNone());
    }

    private void sniff(String html) throws Exception {
        // An explicit feed keeps this test independent of feed endpoint guessing.
        String document = "<html><head><link rel='alternate' type='application/rss+xml' href='/rss.xml'>"
                + html + "</head></html>";

        when(fetcher.fetchContent(eq(rootUrl), any(), any(), any(), any(), any()))
                .thenReturn(new HttpFetchResult.ResultOk(rootUrl.asURI(), 200,
                        new Header[] { new BasicHeader("Content-Type", "text/html; charset=UTF-8") },
                        "127.0.0.1", document.getBytes(StandardCharsets.UTF_8)));

        crawler.sniffRootDocument(rootUrl, robots, timer);
    }

    @Test
    void testAbsolute() throws Exception {
        sniff("<link rel='human-json' href='https://example.com/human.json'>");

        verify(fetcher).fetchContent(eq(new EdgeUrl("https://example.com/human.json")),
                same(recorder), any(), same(timer), any(), eq(HttpFetcher.ProbeType.DISABLED));
    }

    @Test
    void testBase() throws Exception {
        sniff("<base href='/metadata/'><link rel='human-json' href='human.json'>");

        verify(fetcher).fetchContent(eq(new EdgeUrl("https://example.com/metadata/human.json")),
                same(recorder), any(), same(timer), any(), eq(HttpFetcher.ProbeType.DISABLED));
    }

    @Test
    void testBadLinks() throws Exception {
        sniff("""
                <link rel='human-json'>
                <link rel='human-json' href='   '>
                <link rel='human-json' href='https://other.example/human.json'>
                <link rel='human-json' href='ftp://example.com/human.json'>
                <link rel='human-json' href='http://['>
                <link rel='stylesheet' href='/human.json'>
                """);

        verifyNoInteractions(recorder);
        // Only the root and the default favicon should be fetched.
        verify(fetcher, times(2)).fetchContent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testRobotsTxt() throws Exception {
        EdgeUrl humanJsonUrl = new EdgeUrl("https://example.com/human.json");
        when(robots.isAllowed(humanJsonUrl.toString())).thenReturn(false);

        sniff("<link rel='human-json' href='/human.json'>");

        verifyNoInteractions(recorder);
        verify(fetcher, never()).fetchContent(eq(humanJsonUrl), any(), any(), any(), any(), any());
    }
}
