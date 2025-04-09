package nu.marginalia.crawl.fetcher;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import nu.marginalia.UserAgent;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URISyntaxException;

@Tag("slow")
class HttpFetcherImplContentTypeProbeTest {

    private HttpFetcherImpl fetcher;
    private  static WireMockServer wireMockServer;

    private static EdgeUrl timeoutUrl;
    private static EdgeUrl contentTypeHtmlUrl;
    private static EdgeUrl contentTypeBinaryUrl;
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
                        .withFixedDelay(15000))); // 10 seconds delay to simulate timeout

        contentTypeHtmlUrl = new EdgeUrl("http://localhost:18089/test.html.bin");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(contentTypeHtmlUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withStatus(200)));

        contentTypeBinaryUrl = new EdgeUrl("http://localhost:18089/test.bad.bin");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(contentTypeBinaryUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/octet-stream")
                        .withStatus(200)));

        redirectUrl = new EdgeUrl("http://localhost:18089/redirect.bin");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(redirectUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Location", "http://localhost:18089/test.html.bin")
                        .withStatus(301)));

        badHttpStatusUrl = new EdgeUrl("http://localhost:18089/badstatus.bin");
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(badHttpStatusUrl.path))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/html")
                        .withStatus(500)));

        wireMockServer.start();

    }

    @AfterAll
    public static void tearDownAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    public void setUp() {
        fetcher = new HttpFetcherImpl(new UserAgent("test.marginalia.nu", "test.marginalia.nu"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        fetcher.close();
    }

    @Test
    public void testProbeContentTypeHtmlShortcircuitPath() throws URISyntaxException {
        var result = fetcher.probeContentType(new EdgeUrl("https://localhost/test.html"),  ContentTags.empty());
        Assertions.assertInstanceOf(HttpFetcher.ContentTypeProbeResult.Ok.class, result);
    }


    @Test
    public void testProbeContentTypeHtmlShortcircuitTags() {
        var result = fetcher.probeContentType(contentTypeBinaryUrl,  new ContentTags("a", "b"));
        Assertions.assertInstanceOf(HttpFetcher.ContentTypeProbeResult.Ok.class, result);
    }

    @Test
    public void testProbeContentTypeHtml() {
        var result = fetcher.probeContentType(contentTypeHtmlUrl,  ContentTags.empty());
        Assertions.assertEquals(new HttpFetcher.ContentTypeProbeResult.Ok(contentTypeHtmlUrl), result);
    }

    @Test
    public void testProbeContentTypeBinary() {
        var result = fetcher.probeContentType(contentTypeBinaryUrl, ContentTags.empty());
        Assertions.assertEquals(new HttpFetcher.ContentTypeProbeResult.BadContentType("application/octet-stream", 200), result);
    }

    @Test
    public void testProbeContentTypeRedirect() {
        var result = fetcher.probeContentType(redirectUrl, ContentTags.empty());
        Assertions.assertEquals(new HttpFetcher.ContentTypeProbeResult.Redirect(contentTypeHtmlUrl), result);
    }

    @Test
    public void testProbeContentTypeBadHttpStatus() {
        var result = fetcher.probeContentType(badHttpStatusUrl, ContentTags.empty());
        Assertions.assertEquals(new HttpFetcher.ContentTypeProbeResult.HttpError(500, "Bad status code"), result);
    }


    @Test
    public void testTimeout() {
        var result = fetcher.probeContentType(timeoutUrl, ContentTags.empty());
        Assertions.assertInstanceOf(HttpFetcher.ContentTypeProbeResult.Timeout.class, result);
    }

}