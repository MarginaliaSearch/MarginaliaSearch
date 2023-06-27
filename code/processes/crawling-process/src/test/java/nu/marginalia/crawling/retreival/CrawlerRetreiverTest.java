package nu.marginalia.crawling.retreival;

import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.crawling.model.SerializableCrawlData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
class CrawlerRetreiverTest {
    private HttpFetcher httpFetcher;

    @BeforeEach
    public void setUp() {
        httpFetcher = new HttpFetcherImpl("search.marginalia.nu; testing a bit :D");
    }

    @SneakyThrows
    public static void setUpAll() {
        // this must be done to avoid java inserting its own user agent for the sitemap requests
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());
    }

    @Test
    public void testWithKnownDomains() {
        var specs = CrawlingSpecification
                .builder()
                .id("whatever")
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/misc/debian-laptop-install-log/"))
                .build();

        List<SerializableCrawlData> data = new ArrayList<>();

        new CrawlerRetreiver(httpFetcher, specs, data::add).fetch();

        var fetchedUrls =
                data.stream().filter(CrawledDocument.class::isInstance)
                        .map(CrawledDocument.class::cast)
                        .map(doc -> doc.url)
                        .collect(Collectors.toSet());

        assertTrue(fetchedUrls.contains("https://www.marginalia.nu/"));
        assertTrue(fetchedUrls.contains("https://www.marginalia.nu/misc/debian-laptop-install-log/"));

        data.stream().filter(CrawledDocument.class::isInstance)
                .map(CrawledDocument.class::cast)
                .forEach(doc -> System.out.println(doc.url + "\t" + doc.crawlerStatus + "\t" + doc.httpStatus));

    }

    @Test
    public void testEmptySet() {

        var specs = CrawlingSpecification
                .builder()
                .id("whatever")
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of())
                .build();

        List<SerializableCrawlData> data = new ArrayList<>();

        new CrawlerRetreiver(httpFetcher, specs, data::add).fetch();

        data.stream().filter(CrawledDocument.class::isInstance)
                .map(CrawledDocument.class::cast)
                .forEach(doc -> System.out.println(doc.url + "\t" + doc.crawlerStatus + "\t" + doc.httpStatus));

        var fetchedUrls =
                data.stream().filter(CrawledDocument.class::isInstance)
                        .map(CrawledDocument.class::cast)
                        .map(doc -> doc.url)
                        .collect(Collectors.toSet());

        assertTrue(fetchedUrls.contains("https://www.marginalia.nu/"));

        Assertions.assertTrue(
                data.stream().filter(CrawledDocument.class::isInstance)
                    .map(CrawledDocument.class::cast)
                    .anyMatch(doc -> "OK".equals(doc.crawlerStatus))
        );
    }

}