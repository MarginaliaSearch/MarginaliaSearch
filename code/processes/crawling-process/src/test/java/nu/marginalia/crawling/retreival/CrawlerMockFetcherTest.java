package nu.marginalia.crawling.retreival;

import crawlercommons.robots.SimpleRobotRules;
import lombok.SneakyThrows;
import nu.marginalia.bigstring.BigString;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.fetcher.FetchResult;
import nu.marginalia.crawl.retreival.fetcher.FetchResultState;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.SitemapRetriever;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.test.CommonTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrawlerMockFetcherTest {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerMockFetcherTest.class);

    Map<EdgeUrl, CrawledDocument> mockData = new HashMap<>();
    HttpFetcher fetcherMock = new MockFetcher();
    SitemapRetriever sitemapRetriever = new SitemapRetriever();

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
                .documentBody(BigString.encode(documentData))
                .build());
    }

    @SneakyThrows
    private void registerUrlClasspathData(EdgeUrl url, String path) {
        var data = BigString.encode(CommonTestData.loadTestData(path));

        mockData.put(url, CrawledDocument.builder()
                .crawlId("1")
                .url(url.toString())
                .contentType("text/html")
                .httpStatus(200)
                .crawlerStatus(CrawlerDocumentStatus.OK.name())
                .documentBody(data)
                .build());

    }

    @Test
    public void testLemmy() throws URISyntaxException {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://startrek.website/"), "mock-crawl-data/lemmy/index.html");
        registerUrlClasspathData(new EdgeUrl("https://startrek.website/c/startrek"), "mock-crawl-data/lemmy/c_startrek.html");
        registerUrlClasspathData(new EdgeUrl("https://startrek.website/post/108995"), "mock-crawl-data/lemmy/108995.html");

        new CrawlerRetreiver(fetcherMock, new CrawlingSpecification("1", 10, "startrek.website", new ArrayList<>()), out::add)
                .withNoDelay()
                .fetch();

        out.forEach(System.out::println);
    }

    @Test
    public void testMediawiki() throws URISyntaxException {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://en.wikipedia.org/"), "mock-crawl-data/mediawiki/index.html");

        new CrawlerRetreiver(fetcherMock, new CrawlingSpecification("1", 10, "en.wikipedia.org", new ArrayList<>()), out::add)
                .withNoDelay()
                .fetch();

        out.forEach(System.out::println);
    }

    @Test
    public void testDiscourse() throws URISyntaxException {
        List<SerializableCrawlData> out = new ArrayList<>();

        registerUrlClasspathData(new EdgeUrl("https://community.tt-rss.org/"), "mock-crawl-data/discourse/index.html");
        registerUrlClasspathData(new EdgeUrl("https://community.tt-rss.org/t/telegram-channel-to-idle-on/3501"), "mock-crawl-data/discourse/telegram.html");
        registerUrlClasspathData(new EdgeUrl("https://community.tt-rss.org/t/combined-mode-but-grid/4489"), "mock-crawl-data/discourse/grid.html");

        new CrawlerRetreiver(fetcherMock, new CrawlingSpecification("1", 100, "community.tt-rss.org", new ArrayList<>()), out::add)
                .withNoDelay()
                .fetch();

        out.forEach(System.out::println);
    }

    class MockFetcher implements HttpFetcher {

        @Override
        public void setAllowAllContentTypes(boolean allowAllContentTypes) {}

        @Override
        public List<String> getCookies() { return List.of();}

        @Override
        public void clearCookies() {}

        @Override
        public FetchResult probeDomain(EdgeUrl url) {
            logger.info("Probing {}", url);
            return new FetchResult(FetchResultState.OK, url.domain);
        }

        @Override
        public CrawledDocument fetchContent(EdgeUrl url) {
            logger.info("Fetching {}", url);
            if (mockData.containsKey(url)) {
                return mockData.get(url);
            }
            else {
                return CrawledDocument.builder()
                        .crawlId("1")
                        .url(url.toString())
                        .contentType("text/html")
                        .httpStatus(404)
                        .crawlerStatus(CrawlerDocumentStatus.ERROR.name())
                        .build();
            }
        }

        @Override
        public SimpleRobotRules fetchRobotRules(EdgeDomain domain) {
            return new SimpleRobotRules();
        }

        @Override
        public SitemapRetriever createSitemapRetriever() {
            return Mockito.mock(SitemapRetriever.class);
        }
    }
}
