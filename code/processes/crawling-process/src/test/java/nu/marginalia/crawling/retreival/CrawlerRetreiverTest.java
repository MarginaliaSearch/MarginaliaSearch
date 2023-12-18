package nu.marginalia.crawling.retreival;

import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.DomainProber;
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
import org.netpreserve.jwarc.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
class CrawlerRetreiverTest {
    private HttpFetcher httpFetcher;

    Path tempFile;
    Path tempFile2;
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

    @AfterEach
    public void tearDown() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
        if (tempFile2 != null) {
            Files.deleteIfExists(tempFile2);
        }
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

            try (var recorder = new WarcRecorder(tempFile)) {
                new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch();
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
    public void testWithKnownDomains() throws IOException {
        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/misc/debian-laptop-install-log/"))
                .build();

        List<SerializableCrawlData> data = new ArrayList<>();

        tempFile = Files.createTempFile("crawling-process", ".warc");

        try (var recorder = new WarcRecorder(tempFile)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch();
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }


        try (var stream = CrawledDomainReader.createDataStream(tempFile)) {
            while (stream.hasNext()) {
                if (stream.next() instanceof CrawledDocument doc) {
                    data.add(doc);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var fetchedUrls =
                data.stream()
                        .peek(System.out::println)
                        .filter(CrawledDocument.class::isInstance)
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
    public void testEmptySet() throws IOException {

        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of())
                .build();


        List<SerializableCrawlData> data = new ArrayList<>();

        tempFile = Files.createTempFile("crawling-process", ".warc");

        try (var recorder = new WarcRecorder(tempFile)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch();
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }


        try (var stream = CrawledDomainReader.createDataStream(tempFile)) {
            while (stream.hasNext()) {
                if (stream.next() instanceof CrawledDocument doc) {
                    data.add(doc);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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


        tempFile = Files.createTempFile("crawling-process", ".warc.gz");
        tempFile2 = Files.createTempFile("crawling-process", ".warc.gz");

        Map<Class<? extends SerializableCrawlData>, List<SerializableCrawlData>> data = new HashMap<>();

        try (var recorder = new WarcRecorder(tempFile)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch();
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

        try (var stream = CrawledDomainReader.createDataStream(tempFile)) {
            while (stream.hasNext()) {
                var doc = stream.next();
                data.computeIfAbsent(doc.getClass(), c -> new ArrayList<>()).add(doc);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var stream = CrawledDomainReader.createDataStream(tempFile);

        System.out.println("---");

        CrawledDomain domain = (CrawledDomain) data.get(CrawledDomain.class).get(0);
        domain.doc = data.get(CrawledDocument.class).stream().map(CrawledDocument.class::cast).collect(Collectors.toList());
        try (var recorder = new WarcRecorder(tempFile2)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch(new DomainLinks(),
                    new CrawlDataReference(stream));
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }

        try (var reader = new WarcReader(tempFile2)) {
            WarcXResponseReference.register(reader);

            reader.forEach(record -> {
                if (record instanceof WarcResponse rsp) {
                    try {
                        System.out.println(rsp.type() + ":" + rsp.target() + "/" + rsp.http().status());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (record instanceof WarcMetadata rsp) {
                    System.out.println("meta:" + rsp.target());
                }
            });
        }

        try (var ds = CrawledDomainReader.createDataStream(tempFile2)) {
            while (ds.hasNext()) {
                var doc = ds.next();
                if (doc instanceof CrawledDomain dr) {
                    System.out.println(dr.domain + "/" + dr.crawlerStatus);
                }
                else if (doc instanceof CrawledDocument dc) {
                    System.out.println(dc.url + "/" + dc.crawlerStatus + "/" + dc.httpStatus + "/" + dc.timestamp);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }
}