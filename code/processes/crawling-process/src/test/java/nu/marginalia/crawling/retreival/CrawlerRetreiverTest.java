package nu.marginalia.crawling.retreival;

import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.io.CrawledDomainWriter;
import nu.marginalia.crawling.io.CrawlerOutputFile;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.crawling.model.SerializableCrawlData;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @BeforeAll
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

    @Test
    public void testRecrawl() throws IOException {

        var specs = CrawlingSpecification
                .builder()
                .id("123456")
                .crawlDepth(12)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/some-dead-link"))
                .build();


        Path out = Files.createTempDirectory("crawling-process");
        var writer = new CrawledDomainWriter(out, "www.marginalia.nu", "123456");
        Map<Class<? extends SerializableCrawlData>, List<SerializableCrawlData>> data = new HashMap<>();

        new CrawlerRetreiver(httpFetcher, specs, d -> {
            data.computeIfAbsent(d.getClass(), k->new ArrayList<>()).add(d);
            if (d instanceof CrawledDocument doc) {
                System.out.println(doc.url + ": " + doc.recrawlState + "\t" + doc.httpStatus);
            }
            writer.accept(d);
        }).fetch();
        writer.close();

        var reader = new CrawledDomainReader();
        var iter = reader.createIterator(out, specs);

        CrawledDomain domain = (CrawledDomain) data.get(CrawledDomain.class).get(0);
        domain.doc = data.get(CrawledDocument.class).stream().map(CrawledDocument.class::cast).collect(Collectors.toList());

        new CrawlerRetreiver(httpFetcher, specs, d -> {
            if (d instanceof CrawledDocument doc) {
                System.out.println(doc.url + ": " + doc.recrawlState + "\t" + doc.httpStatus);
            }
        }).fetch(new CrawlDataReference(iter));

    }
}