package nu.marginalia.crawling.retreival;

import lombok.SneakyThrows;
import nu.marginalia.UserAgent;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.crawl.retreival.*;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcher;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.io.CrawledDomainReader;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.crawling.model.SerializableCrawlData;
import nu.marginalia.crawling.parquet.CrawledDocumentParquetRecordFileWriter;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;
import org.junit.jupiter.api.*;
import org.netpreserve.jwarc.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
class CrawlerRetreiverTest {
    private HttpFetcher httpFetcher;

    Path tempFileWarc1;
    Path tempFileParquet1;
    Path tempFileWarc2;
    Path tempFileParquet2;
    Path tempFileWarc3;
    @BeforeEach
    public void setUp() throws IOException {
        httpFetcher = new HttpFetcherImpl("search.marginalia.nu; testing a bit :D");
        tempFileParquet1 = Files.createTempFile("crawling-process", ".parquet");
        tempFileParquet2 = Files.createTempFile("crawling-process", ".parquet");

    }

    @SneakyThrows
    @BeforeAll
    public static void setUpAll() {
        // this must be done to avoid java inserting its own user agent for the sitemap requests
        System.setProperty("http.agent", WmsaHome.getUserAgent().uaString());
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempFileWarc1 != null) {
            Files.deleteIfExists(tempFileWarc1);
        }
        if (tempFileParquet1 != null) {
            Files.deleteIfExists(tempFileParquet1);
        }
        if (tempFileWarc2 != null) {
            Files.deleteIfExists(tempFileWarc2);
        }
        if (tempFileParquet2 != null) {
            Files.deleteIfExists(tempFileParquet2);
        }
        if (tempFileWarc3 != null) {
            Files.deleteIfExists(tempFileWarc3);
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

            doCrawl(tempFile, specs);

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

    @SneakyThrows
    @Test
    public void testResync() throws IOException {
        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/misc/debian-laptop-install-log/"))
                .build();
        tempFileWarc1 = Files.createTempFile("crawling-process", "warc");
        tempFileWarc2 = Files.createTempFile("crawling-process", "warc");

        doCrawl(tempFileWarc1, specs);

        Set<String> requests = new HashSet<>();
        Set<String> responses = new HashSet<>();

        var revisitCrawlFrontier = new DomainCrawlFrontier(
                new EdgeDomain("www.marginalia.nu"),
                List.of(), 100);
        var resync = new CrawlerWarcResynchronizer(revisitCrawlFrontier,
                new WarcRecorder(tempFileWarc2)
        );

        // truncate the size of the file to simulate a crash
        simulatePartialWrite(tempFileWarc1);

        resync.run(tempFileWarc1);
        assertTrue(revisitCrawlFrontier.addKnown(new EdgeUrl("https://www.marginalia.nu/misc/debian-laptop-install-log/")));

        try (var reader = new WarcReader(tempFileWarc2)) {
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

    @Test
    public void testWithKnownDomains() throws IOException {
        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(5)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/misc/debian-laptop-install-log/"))
                .build();

        List<SerializableCrawlData> data = new ArrayList<>();

        tempFileWarc1 = Files.createTempFile("crawling-process", ".warc");

        doCrawl(tempFileWarc1, specs);

        convertToParquet(tempFileWarc1, tempFileParquet1);

        try (var stream = CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.ANY, tempFileParquet1)) {
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

        tempFileWarc1 = Files.createTempFile("crawling-process", ".warc");

        doCrawl(tempFileWarc1, specs);
        convertToParquet(tempFileWarc1, tempFileParquet1);

        try (var stream = CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.ANY, tempFileParquet1)) {
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


        tempFileWarc1 = Files.createTempFile("crawling-process", ".warc.gz");
        tempFileWarc2 = Files.createTempFile("crawling-process", ".warc.gz");

        doCrawl(tempFileWarc1, specs);
        convertToParquet(tempFileWarc1, tempFileParquet1);
        doCrawlWithReferenceStream(specs,
                CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.ANY, tempFileParquet1)
        );
        convertToParquet(tempFileWarc2, tempFileParquet2);

        try (var reader = new WarcReader(tempFileWarc2)) {
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

        try (var ds = CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.ANY, tempFileParquet2)) {
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

    private void convertToParquet(Path tempFileWarc2, Path tempFileParquet2) {
        CrawledDocumentParquetRecordFileWriter.convertWarc("www.marginalia.nu",
                new UserAgent("test", "test"), tempFileWarc2, tempFileParquet2);
    }


    @SneakyThrows
    @Test
    public void testRecrawlWithResync() throws IOException {

        var specs = CrawlSpecRecord
                .builder()
                .crawlDepth(12)
                .domain("www.marginalia.nu")
                .urls(List.of("https://www.marginalia.nu/some-dead-link"))
                .build();


        tempFileWarc1 = Files.createTempFile("crawling-process", ".warc.gz");
        tempFileWarc2 = Files.createTempFile("crawling-process", ".warc.gz");
        tempFileWarc3 = Files.createTempFile("crawling-process", ".warc.gz");

        Map<Class<? extends SerializableCrawlData>, List<SerializableCrawlData>> data = new HashMap<>();

        doCrawl(tempFileWarc1, specs);

        convertToParquet(tempFileWarc1, tempFileParquet1);

        try (var stream = CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.ANY, tempFileParquet1)) {
            while (stream.hasNext()) {
                var doc = stream.next();
                data.computeIfAbsent(doc.getClass(), c -> new ArrayList<>()).add(doc);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var stream = CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.ANY, tempFileParquet1);

        System.out.println("---");

        doCrawlWithReferenceStream(specs, stream);

        var revisitCrawlFrontier = new DomainCrawlFrontier(
                new EdgeDomain("www.marginalia.nu"),
                List.of(), 100);

        var resync = new CrawlerWarcResynchronizer(revisitCrawlFrontier,
                new WarcRecorder(tempFileWarc3)
        );

        // truncate the size of the file to simulate a crash
        simulatePartialWrite(tempFileWarc2);

        resync.run(tempFileWarc2);

        assertTrue(revisitCrawlFrontier.addKnown(new EdgeUrl("https://www.marginalia.nu/")));
        convertToParquet(tempFileWarc3, tempFileParquet2);


        try (var reader = new WarcReader(tempFileWarc3)) {
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

        try (var ds = CrawledDomainReader.createDataStream(CrawledDomainReader.CompatibilityLevel.ANY, tempFileParquet2)) {
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

    private void simulatePartialWrite(Path tempFileWarc2) throws IOException {
        try (var raf = new RandomAccessFile(tempFileWarc2.toFile(), "rw")) {
            raf.setLength(raf.length() - 10);
        }
    }

    private void doCrawlWithReferenceStream(CrawlSpecRecord specs, SerializableCrawlDataStream stream) {
        try (var recorder = new WarcRecorder(tempFileWarc2)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch(new DomainLinks(),
                    new CrawlDataReference(stream));
        }
        catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    private void doCrawl(Path tempFileWarc1, CrawlSpecRecord specs) {
        try (var recorder = new WarcRecorder(tempFileWarc1)) {
            new CrawlerRetreiver(httpFetcher, new DomainProber(d -> true), specs, recorder).fetch();
        } catch (IOException ex) {
            Assertions.fail(ex);
        }
    }
}