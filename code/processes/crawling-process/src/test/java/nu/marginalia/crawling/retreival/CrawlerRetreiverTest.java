package nu.marginalia.crawling.retreival;

import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.io.CrawledDomainWriter;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.junit.jupiter.api.*;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRequest;
import org.netpreserve.jwarc.WarcResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void testWarcOutput() throws IOException {
        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/misc/debian-laptop-install-log/"))
                .build();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("crawling-process", "warc");

            List<SerializableCrawlData> data = new ArrayList<>();

            try (var recorder = new WarcRecorder(tempFile)) {
                new CrawlerRetreiver(httpFetcher, specs, recorder, data::add).fetch();
            } catch (IOException ex) {
                Assertions.fail(ex);
            }

            Set<String> requests = new HashSet<>();
            Set<String> responses = new HashSet<>();

            try (var reader = new WarcReader(tempFile)) {
                reader.forEach(record -> {
                    if (record instanceof WarcRequest req) {
                        requests.add(req.target());
                        System.out.println(req.type() + ":" + req.target());
                    }
                    else if (record instanceof WarcResponse rsp) {
                        responses.add(rsp.target());
                        System.out.println(rsp.type() + ":" + rsp.target());
                    }
                    else {
                        System.out.println(record.type());
                    }
                });
            }

            assertTrue(requests.contains("https://www.marginalia.nu/misc/debian-laptop-install-log/"));
            assertEquals(requests, responses);
        }
        finally {
            if (tempFile != null)
                Files.deleteIfExists(tempFile);
        }
    }
    @Test
    public void testWithKnownDomains() {
        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/misc/debian-laptop-install-log/"))
                .build();

        List<SerializableCrawlData> data = new ArrayList<>();

        try (var recorder = new WarcRecorder()) {
            new CrawlerRetreiver(httpFetcher, specs, recorder, data::add).fetch();
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

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

        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of())
                .build();

        List<SerializableCrawlData> data = new ArrayList<>();

        try (var recorder = new WarcRecorder()) {
            new CrawlerRetreiver(httpFetcher, specs, recorder, data::add).fetch();
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

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

        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(12)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/some-dead-link"))
                .build();


        Path out = Files.createTempDirectory("crawling-process");
        var writer = new CrawledDomainWriter(out, specs.domain, "idid");
        Map<Class<? extends SerializableCrawlData>, List<SerializableCrawlData>> data = new HashMap<>();

        try (var recorder = new WarcRecorder()) {
            new CrawlerRetreiver(httpFetcher, specs, recorder, d -> {
                data.computeIfAbsent(d.getClass(), k->new ArrayList<>()).add(d);
                if (d instanceof CrawledDocument doc) {
                    System.out.println(doc.url + ": " + doc.recrawlState + "\t" + doc.httpStatus);
                    if (Math.random() > 0.5) {
                        doc.headers = "";
                    }
                }
                writer.accept(d);
            }).fetch();
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }


        writer.close();

        var reader = new CrawledDomainReader();
        var stream = reader.createDataStream(out, specs.domain, "idid");

        CrawledDomain domain = (CrawledDomain) data.get(CrawledDomain.class).get(0);
        domain.doc = data.get(CrawledDocument.class).stream().map(CrawledDocument.class::cast).collect(Collectors.toList());
        try (var recorder = new WarcRecorder()) {
            new CrawlerRetreiver(httpFetcher, specs, recorder, d -> {
                if (d instanceof CrawledDocument doc) {
                    System.out.println(doc.url + ": " + doc.recrawlState + "\t" + doc.httpStatus);
                }
            }).fetch(new DomainLinks(), new CrawlDataReference(stream));
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }
    }
}