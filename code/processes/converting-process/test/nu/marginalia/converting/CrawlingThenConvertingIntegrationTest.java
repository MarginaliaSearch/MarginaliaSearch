package nu.marginalia.converting;

import com.google.inject.Guice;
import com.google.inject.Injector;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.crawl.CrawlerMain;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainProber;
import nu.marginalia.io.crawldata.format.ParquetSerializableCrawlDataStream;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.model.crawldata.SerializableCrawlData;
import nu.marginalia.parquet.crawldata.CrawledDocumentParquetRecordFileWriter;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the crawler and converter integration.  These are pretty slow and potentially
 * a bit flaky, since they attempt to fetch real websites.
 */
@Tag("slow")
public class CrawlingThenConvertingIntegrationTest {
    private DomainProcessor domainProcessor;
    private HttpFetcher httpFetcher;

    private static final Logger logger = LoggerFactory.getLogger(CrawlingThenConvertingIntegrationTest.class);

    private Path fileName;
    private Path fileName2;

    @BeforeAll
    public static void setUpAll() {
        // this must be done to avoid java inserting its own user agent for the sitemap requests
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());
    }

    @BeforeEach
    public void setUp() throws IOException {
        Injector injector = Guice.createInjector(
                new ConvertingIntegrationTestModule()
        );

        domainProcessor = injector.getInstance(DomainProcessor.class);
        httpFetcher = new HttpFetcherImpl(WmsaHome.getUserAgent().uaString());
        this.fileName = Files.createTempFile("crawling-then-converting", ".warc.gz");
        this.fileName2 = Files.createTempFile("crawling-then-converting", ".warc.gz");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(fileName);
        Files.deleteIfExists(fileName2);
    }

    @Test
    public void testInvalidDomain() throws IOException {
        // Attempt to fetch an invalid domain
        var specs = new CrawlerMain.CrawlSpecRecord("invalid.invalid.invalid", 10);

        CrawledDomain crawlData = crawl(specs);

        assertEquals("ERROR", crawlData.crawlerStatus);
        assertTrue(crawlData.doc.isEmpty());

        var processedData = process();

        assertNotNull(processedData);
        assertTrue(processedData.documents.isEmpty());
    }

    @Test
    public void testRedirectingDomain() throws IOException {
        // Attempt to fetch an invalid domain
        var specs = new CrawlerMain.CrawlSpecRecord("memex.marginalia.nu", 10);

        CrawledDomain crawlData = crawl(specs);

        System.out.println(crawlData);

        assertEquals("REDIRECT", crawlData.crawlerStatus);
        assertEquals("www.marginalia.nu", crawlData.redirectDomain);
        assertTrue(crawlData.doc.isEmpty());

        var processedData = process();

        assertNotNull(processedData);
        assertTrue(processedData.documents.isEmpty());
    }

    @Test
    public void testBlockedDomain() throws IOException {
        // Attempt to fetch an invalid domain
        var specs = new CrawlerMain.CrawlSpecRecord("search.marginalia.nu", 10);

        CrawledDomain crawlData = crawl(specs, d->false); // simulate blocking by blacklisting everything

        assertEquals("ERROR", crawlData.crawlerStatus);
        assertEquals("BLOCKED;IP not allowed", crawlData.crawlerStatusDesc);
        assertTrue(crawlData.doc.isEmpty());

        var processedData = process();

        assertNotNull(processedData);
        assertTrue(processedData.documents.isEmpty());
    }

    @Test
    public void crawlSunnyDay() throws IOException {
        var specs = new CrawlerMain.CrawlSpecRecord("www.marginalia.nu", 10);

        CrawledDomain domain = crawl(specs);
        assertFalse(domain.doc.isEmpty());
        assertEquals("OK", domain.crawlerStatus);
        assertEquals("www.marginalia.nu", domain.domain);

        boolean hasRobotsTxt = domain.doc.stream().map(doc -> doc.url).anyMatch(url -> url.endsWith("/robots.txt"));
        assertFalse(hasRobotsTxt, "Robots.txt should not leave the crawler");

        var output = process();

        assertNotNull(output);
        assertFalse(output.documents.isEmpty());
        assertEquals(new EdgeDomain("www.marginalia.nu"), output.domain);
        assertEquals(DomainIndexingState.ACTIVE, output.state);


        for (var doc : output.documents) {
            if (doc.isOk()) {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.details.title);
            }
            else {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.stateReason);
            }
        }

    }



    @Test
    public void crawlContentTypes() throws IOException {
        var specs = new CrawlerMain.CrawlSpecRecord("www.marginalia.nu", 10,
                List.of(
                        "https://www.marginalia.nu/sanic.png",
                        "https://www.marginalia.nu/invalid"
                )
        );

        CrawledDomain domain = crawl(specs);
        assertFalse(domain.doc.isEmpty());
        assertEquals("OK", domain.crawlerStatus);
        assertEquals("www.marginalia.nu", domain.domain);

        Set<String> allUrls = domain.doc.stream().map(doc -> doc.url).collect(Collectors.toSet());
        assertTrue(allUrls.contains("https://www.marginalia.nu/sanic.png"), "Should have record for image despite blocked content type");
        assertTrue(allUrls.contains("https://www.marginalia.nu/invalid"), "Should have have record for invalid URL");

        var output = process();

        assertNotNull(output);
        assertFalse(output.documents.isEmpty());
        assertEquals(new EdgeDomain("www.marginalia.nu"), output.domain);
        assertEquals(DomainIndexingState.ACTIVE, output.state);


        for (var doc : output.documents) {
            if (doc.isOk()) {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.details.title);
            }
            else {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.stateReason);
            }
        }

    }


    @Test
    public void crawlRobotsTxt() throws IOException {
        var specs = new CrawlerMain.CrawlSpecRecord("search.marginalia.nu", 5,
                        List.of("https://search.marginalia.nu/search?q=hello+world")
        );

        CrawledDomain domain = crawl(specs);
        assertFalse(domain.doc.isEmpty());
        assertEquals("OK", domain.crawlerStatus);
        assertEquals("search.marginalia.nu", domain.domain);

        Set<String> allUrls = domain.doc.stream().map(doc -> doc.url).collect(Collectors.toSet());
        assertTrue(allUrls.contains("https://search.marginalia.nu/search"), "We expect a record for entities that are forbidden");

        var output = process();

        assertNotNull(output);
        assertFalse(output.documents.isEmpty());
        assertEquals(new EdgeDomain("search.marginalia.nu"), output.domain);
        assertEquals(DomainIndexingState.ACTIVE, output.state);

        for (var doc : output.documents) {
            if (doc.isOk()) {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.details.title);
            }
            else {
                System.out.println(doc.url + "\t" + doc.state + "\t" + doc.stateReason);
            }
        }

    }

    private ProcessedDomain process() {
        try (var stream = new ParquetSerializableCrawlDataStream(fileName2)) {
            return domainProcessor.fullProcessing(stream);
        }
        catch (Exception e) {
            Assertions.fail(e);
            return null; // unreachable
        }
    }
    private CrawledDomain crawl(CrawlerMain.CrawlSpecRecord specs) throws IOException {
        return crawl(specs, domain -> true);
    }

    private CrawledDomain crawl(CrawlerMain.CrawlSpecRecord specs, Predicate<EdgeDomain> domainBlacklist) throws IOException {
        List<SerializableCrawlData> data = new ArrayList<>();

        try (var recorder = new WarcRecorder(fileName)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(domainBlacklist), specs, recorder).crawlDomain();
        }

        CrawledDocumentParquetRecordFileWriter.convertWarc(specs.domain(),
                new UserAgent("test", "test"),
                fileName, fileName2);

        try (var reader = new ParquetSerializableCrawlDataStream(fileName2)) {
            while (reader.hasNext()) {
                var next = reader.next();
                logger.info("{}", next);
                data.add(next);
            }
        }

        CrawledDomain domain = data.stream()
                .filter(CrawledDomain.class::isInstance)
                .map(CrawledDomain.class::cast)
                .findFirst()
                .get();

        data.stream().filter(CrawledDocument.class::isInstance).map(CrawledDocument.class::cast).forEach(domain.doc::add);
        return domain;
    }
}
