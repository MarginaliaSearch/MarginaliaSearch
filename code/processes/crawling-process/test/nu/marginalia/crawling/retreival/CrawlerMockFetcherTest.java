package nu.marginalia.crawling.retreival;

import crawlercommons.robots.SimpleRobotRules;
import nu.marginalia.crawl.CrawlerMain;
import nu.marginalia.crawl.DomainStateDb;
import nu.marginalia.crawl.fetcher.*;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainProber;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawlerDocumentStatus;
import nu.marginalia.model.crawldata.SerializableCrawlData;
import nu.marginalia.test.CommonTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrawlerMockFetcherTest {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerMockFetcherTest.class);

    Map<EdgeUrl, CrawledDocument> mockData = new HashMap<>();
    HttpFetcher fetcherMock = new MockFetcher();
    private Path dbTempFile;
    @BeforeEach
    public void setUp() throws IOException {
        dbTempFile = Files.createTempFile("domains","db");
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(dbTempFile);
        mockData.clear();
    }

    private void registerUrl(EdgeUrl url, String documentData) {
        mockData.put(url, CrawledDocument.builder()
                .crawlId("1")
                .url(url.toString())
                .contentType("text/html")
                .httpStatus(200)
                .crawlerStatus(CrawlerDocumentStatus.OK.name())
                .documentBody(documentData)
                .build());
    }

    private void registerUrlClasspathData(EdgeUrl url, String path) {

        mockData.put(url, CrawledDocument.builder()
                .crawlId("1")
                .url(url.toString())
                .contentType("text/html")
                .httpStatus(200)
                .crawlerStatus(CrawlerDocumentStatus.OK.name())
                .documentBody(CommonTestData.loadTestData(path))
                .build());

    }

    void crawl(CrawlerMain.CrawlSpecRecord spec) throws IOException, SQLException {
        try (var recorder = new WarcRecorder();
             var db = new DomainStateDb(dbTempFile)
        ) {
            new CrawlerRetreiver(fetcherMock, new DomainProber(d -> true), spec, db, recorder)
                    .crawlDomain();
        }
    }

    @Test
    public void testLemmy() throws Exception {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://startrek.website/"), "mock-crawl-data/lemmy/index.html");
        registerUrlClasspathData(new EdgeUrl("https://startrek.website/c/startrek"), "mock-crawl-data/lemmy/c_startrek.html");
        registerUrlClasspathData(new EdgeUrl("https://startrek.website/post/108995"), "mock-crawl-data/lemmy/108995.html");

        crawl(new CrawlerMain.CrawlSpecRecord("startrek.website", 10, new ArrayList<>()));
    }

    @Test
    public void testMediawiki() throws Exception {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://en.wikipedia.org/"), "mock-crawl-data/mediawiki/index.html");

        crawl(new CrawlerMain.CrawlSpecRecord("en.wikipedia.org", 10, new ArrayList<>()));
    }

    @Test
    public void testDiscourse() throws Exception {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://community.tt-rss.org/"), "mock-crawl-data/discourse/index.html");
        registerUrlClasspathData(new EdgeUrl("https://community.tt-rss.org/t/telegram-channel-to-idle-on/3501"), "mock-crawl-data/discourse/telegram.html");
        registerUrlClasspathData(new EdgeUrl("https://community.tt-rss.org/t/combined-mode-but-grid/4489"), "mock-crawl-data/discourse/grid.html");

        crawl(new CrawlerMain.CrawlSpecRecord("community.tt-rss.org", 10, new ArrayList<>()));
    }

    class MockFetcher implements HttpFetcher {

        @Override
        public void setAllowAllContentTypes(boolean allowAllContentTypes) {}

        @Override
        public Cookies getCookies() { return new Cookies();}

        @Override
        public void clearCookies() {}

        @Override
        public HttpFetcherImpl.DomainProbeResult probeDomain(EdgeUrl url) {
            logger.info("Probing {}", url);
            return new HttpFetcher.DomainProbeResult.Ok(url);
        }

        @Override
        public ContentTypeProbeResult probeContentType(EdgeUrl url, WarcRecorder recorder, ContentTags tags) {
            logger.info("Probing {}", url);
            return new HttpFetcher.ContentTypeProbeResult.Ok(url);
        }

        @Override
        public HttpFetchResult fetchContent(EdgeUrl url, WarcRecorder recorder, ContentTags tags, ProbeType probeType) {
            logger.info("Fetching {}", url);
            if (mockData.containsKey(url)) {
                byte[] bodyBytes = mockData.get(url).documentBody.getBytes();

                try {
                    return new HttpFetchResult.ResultOk(
                            url.asURI(),
                            200,
                            HttpHeaders.of(Map.of(), (k,v)->true),
                            "127.0.0.1",
                            bodyBytes,
                            0,
                            bodyBytes.length
                    );
                }
                catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            return new HttpFetchResult.ResultNone();
        }

        @Override
        public SimpleRobotRules fetchRobotRules(EdgeDomain domain, WarcRecorder recorder) {
            return new SimpleRobotRules();
        }

        @Override
        public SitemapRetriever createSitemapRetriever() {
            return Mockito.mock(SitemapRetriever.class);
        }

        @Override
        public void close() {

        }
    }
}
