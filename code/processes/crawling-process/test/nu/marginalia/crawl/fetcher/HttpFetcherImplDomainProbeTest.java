package nu.marginalia.crawl.fetcher;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import nu.marginalia.UserAgent;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawlerDomainStatus;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URISyntaxException;

@Tag("slow")
class HttpFetcherImplDomainProbeTest {

    private HttpFetcherImpl fetcher;
    private  static WireMockServer wireMockServer;

    private static EdgeUrl timeoutUrl;

    @BeforeAll
    public static void setupAll() throws URISyntaxException {
        wireMockServer =
                new WireMockServer(WireMockConfiguration.wireMockConfig()
                        .port(18089));


        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo("/timeout"))
                .willReturn(WireMock.aResponse()
                        .withFixedDelay(15000))); // 10 seconds delay to simulate timeout

        wireMockServer.start();
        timeoutUrl = new EdgeUrl("http://localhost:18089/timeout");
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
    public void testProbeDomain() throws URISyntaxException {
        var result = fetcher.probeDomain(new EdgeUrl("https://www.marginalia.nu/"));
        Assertions.assertEquals(new HttpFetcher.DomainProbeResult.Ok(new EdgeUrl("https://www.marginalia.nu/")), result);
    }

    @Test
    public void testProbeDomainProtoUpgrade() throws URISyntaxException {
        var result = fetcher.probeDomain(new EdgeUrl("http://www.marginalia.nu/"));
        Assertions.assertEquals(new HttpFetcher.DomainProbeResult.Ok(new EdgeUrl("https://www.marginalia.nu/")), result);
    }

    @Test
    public void testProbeDomainRedirect() throws URISyntaxException {
        var result = fetcher.probeDomain(new EdgeUrl("http://search.marginalia.nu/"));
        Assertions.assertEquals(new HttpFetcher.DomainProbeResult.Redirect(new EdgeDomain("marginalia-search.com")), result);
    }

    @Test
    public void testProbeDomainError() throws URISyntaxException {
        var result = fetcher.probeDomain(new EdgeUrl("https://invalid.example.com/"));
        Assertions.assertEquals(new HttpFetcher.DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Error during domain probe"), result);
    }

    @Test
    public void testProbeDomainTimeout() throws URISyntaxException {
        var result = fetcher.probeDomain(timeoutUrl);
        Assertions.assertEquals(new HttpFetcher.DomainProbeResult.Error(CrawlerDomainStatus.ERROR, "Timeout during domain probe"), result);
    }
}