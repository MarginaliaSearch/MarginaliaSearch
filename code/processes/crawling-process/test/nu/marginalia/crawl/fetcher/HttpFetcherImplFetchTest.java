package nu.marginalia.crawl.fetcher;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import nu.marginalia.UserAgent;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import org.junit.jupiter.api.*;
import org.netpreserve.jwarc.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
class HttpFetcherImplFetchTest {

    private HttpFetcherImpl fetcher;
    private static WireMockServer wireMockServer;

    private static String etag = "etag";
    private static String lastModified = "Wed, 21 Oct 2024 07:28:00 GMT";

    private static EdgeUrl okUrl;
    private static EdgeUrl okUrlWith304;

    private static EdgeUrl timeoutUrl;
    private static EdgeUrl redirectUrl;
    private static EdgeUrl badHttpStatusUrl;

    @BeforeAll
    public static void setupAll() throws URISyntaxException {
        wireMockServer =
                new WireMockServer(WireMockConfiguration.wireMockConfig()
                        .port(18089));

        timeoutUrl = new EdgeUrl("http://localhost:18089/timeout.bin");

        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(timeoutUrl.path))
                .willReturn(WireMock.aResponse()
                        .withFixedDelay(15000)
                )); // 15 seconds delay to simulate timeout
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(timeoutUrl.path))
                .willReturn(WireMock.aResponse()
                        .withFixedDelay(15000)
                        .withBody("Hello World")
                )); // 15 seconds delay to simulate timeout

        redirectUrl = new EdgeUrl("http://localhost:18089/redirect.bin");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(redirectUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Location", "http://localhost:18089/test.html.bin")
                        .withStatus(301)));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(redirectUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Location", "http://localhost:18089/test.html.bin")
                        .withStatus(301)));

        badHttpStatusUrl = new EdgeUrl("http://localhost:18089/badstatus");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(badHttpStatusUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withStatus(500)));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(badHttpStatusUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withStatus(500)));

        okUrl = new EdgeUrl("http://localhost:18089/ok.bin");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(okUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withStatus(200)));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(okUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withStatus(200)
                        .withBody("Hello World")));

        okUrlWith304 = new EdgeUrl("http://localhost:18089/ok304.bin");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(okUrlWith304.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withHeader("ETag", etag)
                        .withHeader("Last-Modified", lastModified)
                        .withStatus(304)));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(okUrlWith304.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withHeader("ETag", etag)
                        .withHeader("Last-Modified", lastModified)
                        .withStatus(304)));

        wireMockServer.start();

    }

    @AfterAll
    public static void tearDownAll() {
        wireMockServer.stop();
    }


    WarcRecorder warcRecorder;
    Path warcFile;

    @BeforeEach
    public void setUp() throws IOException {
        fetcher = new HttpFetcherImpl(new UserAgent("test.marginalia.nu", "test.marginalia.nu"));
        warcFile = Files.createTempFile(getClass().getSimpleName(), ".warc");
        warcRecorder = new WarcRecorder(warcFile, fetcher);
    }

    @AfterEach
    public void tearDown() throws IOException {
        fetcher.close();
        warcRecorder.close();
        Files.deleteIfExists(warcFile);
    }


    @Test
    public void testOk_NoProbe() throws IOException {
        var result = fetcher.fetchContent(okUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.DISABLED);

        Assertions.assertInstanceOf(HttpFetchResult.ResultOk.class, result);
        Assertions.assertTrue(result.isOk());

        List<WarcRecord> warcRecords = getWarcRecords();
        assertEquals(2, warcRecords.size());
        Assertions.assertInstanceOf(WarcRequest.class, warcRecords.get(0));
        Assertions.assertInstanceOf(WarcResponse.class, warcRecords.get(1));

        WarcResponse response = (WarcResponse) warcRecords.get(1);
        assertEquals("0", response.headers().first("X-Has-Cookies").orElse("0"));
    }

    @Test
    public void testOk_FullProbe() {
        var result = fetcher.fetchContent(okUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.FULL);

        Assertions.assertInstanceOf(HttpFetchResult.ResultOk.class, result);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testOk304_NoProbe() {
        var result = fetcher.fetchContent(okUrlWith304, warcRecorder, new CrawlDelayTimer(1000), new ContentTags(etag, lastModified), HttpFetcher.ProbeType.DISABLED);

        Assertions.assertInstanceOf(HttpFetchResult.Result304Raw.class, result);
        System.out.println(result);

    }

    @Test
    public void testOk304_FullProbe() {
        var result = fetcher.fetchContent(okUrlWith304, warcRecorder, new CrawlDelayTimer(1000), new ContentTags(etag, lastModified), HttpFetcher.ProbeType.FULL);

        Assertions.assertInstanceOf(HttpFetchResult.Result304Raw.class, result);
        System.out.println(result);
    }

    @Test
    public void testBadStatus_NoProbe() throws IOException {
        var result = fetcher.fetchContent(badHttpStatusUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.DISABLED);

        Assertions.assertInstanceOf(HttpFetchResult.ResultOk.class, result);
        Assertions.assertFalse(result.isOk());


        List<WarcRecord> warcRecords = getWarcRecords();
        assertEquals(2, warcRecords.size());
        Assertions.assertInstanceOf(WarcRequest.class, warcRecords.get(0));
        Assertions.assertInstanceOf(WarcResponse.class, warcRecords.get(1));
    }

    @Test
    public void testBadStatus_FullProbe() {
        var result = fetcher.fetchContent(badHttpStatusUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.FULL);

        Assertions.assertInstanceOf(HttpFetchResult.ResultOk.class, result);
        Assertions.assertFalse(result.isOk());

        System.out.println(result);
    }

    @Test
    public void testRedirect_NoProbe() throws URISyntaxException, IOException {
        var result = fetcher.fetchContent(redirectUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.DISABLED);

        Assertions.assertInstanceOf(HttpFetchResult.ResultRedirect.class, result);
        assertEquals(new EdgeUrl("http://localhost:18089/test.html.bin"), ((HttpFetchResult.ResultRedirect) result).url());

        List<WarcRecord> warcRecords = getWarcRecords();
        assertEquals(2, warcRecords.size());
        Assertions.assertInstanceOf(WarcRequest.class, warcRecords.get(0));
        Assertions.assertInstanceOf(WarcResponse.class, warcRecords.get(1));
    }

    @Test
    public void testRedirect_FullProbe() throws URISyntaxException {
        var result = fetcher.fetchContent(redirectUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.FULL);

        Assertions.assertInstanceOf(HttpFetchResult.ResultRedirect.class, result);
        assertEquals(new EdgeUrl("http://localhost:18089/test.html.bin"), ((HttpFetchResult.ResultRedirect) result).url());

        System.out.println(result);
    }


    @Test
    public void testFetchTimeout_NoProbe() throws IOException, URISyntaxException {
        Instant requestStart = Instant.now();

        var result = fetcher.fetchContent(timeoutUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.DISABLED);

        Assertions.assertInstanceOf(HttpFetchResult.ResultException.class, result);

        Instant requestEnd = Instant.now();

        System.out.println(result);

        // Verify that we are actually timing out, and not blocking on the request until it finishes (which would be a bug),
        // the request will take 15 seconds to complete, so we should be able to timeout before that, something like 10 seconds and change;
        // but we'll verify that it is less than 15 seconds to make the test less fragile.

        Assertions.assertTrue(requestEnd.isBefore(requestStart.plusSeconds(15)), "Request should have taken less than 15 seconds");

        var records = getWarcRecords();
        Assertions.assertEquals(1, records.size());
        Assertions.assertInstanceOf(WarcXEntityRefused.class, records.getFirst());
        WarcXEntityRefused entity = (WarcXEntityRefused) records.getFirst();
        assertEquals(WarcXEntityRefused.documentProbeTimeout, entity.profile());
        assertEquals(timeoutUrl.asURI(), entity.targetURI());
    }

    @Test
    public void testFetchTimeout_Probe() throws IOException, URISyntaxException {
        Instant requestStart = Instant.now();
        var result = fetcher.fetchContent(timeoutUrl, warcRecorder, new CrawlDelayTimer(1000), ContentTags.empty(), HttpFetcher.ProbeType.FULL);
        Instant requestEnd = Instant.now();

        Assertions.assertInstanceOf(HttpFetchResult.ResultException.class, result);


        // Verify that we are actually timing out, and not blocking on the request until it finishes (which would be a bug),
        // the request will take 15 seconds to complete, so we should be able to timeout before that, something like 10 seconds and change;
        // but we'll verify that it is less than 15 seconds to make the test less fragile.

        Assertions.assertTrue(requestEnd.isBefore(requestStart.plusSeconds(15)), "Request should have taken less than 15 seconds");

        var records = getWarcRecords();
        Assertions.assertEquals(1, records.size());
        Assertions.assertInstanceOf(WarcXEntityRefused.class, records.getFirst());
        WarcXEntityRefused entity = (WarcXEntityRefused) records.getFirst();
        assertEquals(WarcXEntityRefused.documentProbeTimeout, entity.profile());
        assertEquals(timeoutUrl.asURI(), entity.targetURI());
    }


    private List<WarcRecord> getWarcRecords() throws IOException {
        List<WarcRecord> records = new ArrayList<>();

        System.out.println(Files.readString(warcFile));

        try (var reader = new WarcReader(warcFile)) {
            WarcXResponseReference.register(reader);
            WarcXEntityRefused.register(reader);

            for (var record : reader) {
                records.add(record);
            }
        }

        return records;
    }


}