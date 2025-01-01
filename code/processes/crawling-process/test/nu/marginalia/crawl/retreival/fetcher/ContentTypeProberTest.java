package nu.marginalia.crawl.retreival.fetcher;

import com.sun.net.httpserver.HttpServer;
import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ContentTypeProberTest {

    private static int port;
    private static HttpServer server;
    private static HttpFetcherImpl fetcher;

    static EdgeUrl htmlEndpoint;
    static EdgeUrl htmlRedirEndpoint;
    static EdgeUrl binaryEndpoint;
    static EdgeUrl timeoutEndpoint;

    static Path warcFile;
    static WarcRecorder recorder;

    @BeforeEach
    void setUp() throws IOException  {

        warcFile = Files.createTempFile("test", ".warc");

        Random r = new Random();
        port = r.nextInt(10000) + 8000;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 10);

        server.createContext("/html.gz", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/redir.gz", exchange -> {
            exchange.getResponseHeaders().add("Location", "/html.gz");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        server.createContext("/bin.gz", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/binary");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.createContext("/timeout.gz", exchange -> {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            exchange.getResponseHeaders().add("Content-Type", "application/binary");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.start();

        htmlEndpoint = EdgeUrl.parse("http://localhost:" + port + "/html.gz").get();
        binaryEndpoint = EdgeUrl.parse("http://localhost:" + port + "/bin.gz").get();
        timeoutEndpoint = EdgeUrl.parse("http://localhost:" + port + "/timeout.gz").get();
        htmlRedirEndpoint = EdgeUrl.parse("http://localhost:" + port + "/redir.gz").get();

        fetcher = new HttpFetcherImpl("test");
        recorder = new WarcRecorder(warcFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.stop(0);
        fetcher.close();
        recorder.close();

        Files.deleteIfExists(warcFile);
    }

    @Test
    void probeContentTypeOk() throws Exception {
        HttpFetcher.ContentTypeProbeResult result = fetcher.probeContentType(htmlEndpoint, recorder, ContentTags.empty());

        System.out.println(result);

        assertEquals(result, new HttpFetcher.ContentTypeProbeResult.Ok(htmlEndpoint));
    }

    @Test
    void probeContentTypeRedir() throws Exception {
        HttpFetcher.ContentTypeProbeResult result = fetcher.probeContentType(htmlRedirEndpoint, recorder, ContentTags.empty());

        System.out.println(result);

        assertEquals(result, new HttpFetcher.ContentTypeProbeResult.Ok(htmlEndpoint));
    }

    @Test
    void probeContentTypeBad() throws Exception {
        HttpFetcher.ContentTypeProbeResult result = fetcher.probeContentType(binaryEndpoint, recorder, ContentTags.empty());

        System.out.println(result);

        assertInstanceOf(HttpFetcher.ContentTypeProbeResult.BadContentType.class, result);
    }

    @Test
    void probeContentTypeTimeout() throws Exception {
        HttpFetcher.ContentTypeProbeResult result = fetcher.probeContentType(timeoutEndpoint, recorder, ContentTags.empty());

        System.out.println(result);

        assertInstanceOf(HttpFetcher.ContentTypeProbeResult.Timeout.class, result);
    }
}