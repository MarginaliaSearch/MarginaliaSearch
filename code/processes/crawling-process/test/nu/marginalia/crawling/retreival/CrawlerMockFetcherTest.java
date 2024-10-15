package nu.marginalia.crawling.retreival;

import crawlercommons.robots.SimpleRobotRules;
import lombok.SneakyThrows;
import nu.marginalia.crawl.CrawlerMain;
import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.fetcher.SitemapRetriever;
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
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrawlerMockFetcherTest {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerMockFetcherTest.class);

    Map<EdgeUrl, CrawledDocument> mockData = new HashMap<>();
    HttpFetcher fetcherMock = new MockFetcher();

    @AfterEach
    public void tearDown() {
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

    @SneakyThrows
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

    void crawl(CrawlerMain.CrawlSpecRecord spec)  throws IOException {
        try (var recorder = new WarcRecorder()) {
            new CrawlerRetreiver(fetcherMock, new DomainProber(d -> true), spec, recorder)
                    .crawlDomain();
        }
    }

    @Test
    public void testLemmy() throws URISyntaxException, IOException {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://startrek.website/"), "mock-crawl-data/lemmy/index.html");
        registerUrlClasspathData(new EdgeUrl("https://startrek.website/c/startrek"), "mock-crawl-data/lemmy/c_startrek.html");
        registerUrlClasspathData(new EdgeUrl("https://startrek.website/post/108995"), "mock-crawl-data/lemmy/108995.html");

        crawl(new CrawlerMain.CrawlSpecRecord("startrek.website", 10, new ArrayList<>()));
    }

    @Test
    public void testMediawiki() throws URISyntaxException, IOException {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://en.wikipedia.org/"), "mock-crawl-data/mediawiki/index.html");

        crawl(new CrawlerMain.CrawlSpecRecord("en.wikipedia.org", 10, new ArrayList<>()));
    }

    @Test
    public void testDiscourse() throws URISyntaxException, IOException {
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
        public List<String> getCookies() { return List.of();}

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

        @SneakyThrows
        @Override
        public HttpFetchResult fetchContent(EdgeUrl url, WarcRecorder recorder, ContentTags tags, ProbeType probeType) {
            logger.info("Fetching {}", url);
            if (mockData.containsKey(url)) {
                byte[] bodyBytes = mockData.get(url).documentBody.getBytes();
                return new HttpFetchResult.ResultOk(
                        url.asURI(),
                        200,
                        new Headers.Builder().build(),
                        "127.0.0.1",
                        bodyBytes,
                        0,
                        bodyBytes.length
                );
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

    }
}
