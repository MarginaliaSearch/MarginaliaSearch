package nu.marginalia.crawl.retreival.fetcher;

import com.sun.net.httpserver.HttpServer;
import nu.marginalia.UserAgent;
import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.DomainCookies;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.io.SerializableCrawlDataStream;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.slop.SlopCrawlDataRecord;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRequest;
import org.netpreserve.jwarc.WarcResponse;
import org.netpreserve.jwarc.WarcXResponseReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WarcRecorderTest {
    Path fileNameWarc;
    Path fileNameSlop;
    WarcRecorder client;

    CloseableHttpClient httpClient;
    HttpServer server;
    String baseUrl;
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = HttpClients.custom().disableContentCompression().build();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String contentType = switch (exchange.getRequestURI().getPath()) {
                case "/sanic.png" -> "image/png";
                case "/test.pdf" -> "application/pdf";
                default -> "text/html";
            };
            byte[] body = (contentType.equals("text/html")
                    ? "<!doctype html><html><body>Test document</body></html>"
                    : "test binary content").getBytes(StandardCharsets.UTF_8);
            var compressed = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(compressed)) {
                gzip.write(body);
            }
            body = compressed.toByteArray();
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        fileNameWarc = Files.createTempFile("test", ".warc");
        fileNameSlop = Files.createTempFile("test", ".slop.zip");

        client = new WarcRecorder(fileNameWarc);
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
        httpClient.close();
        server.stop(0);
        Files.delete(fileNameWarc);
        Files.deleteIfExists(fileNameSlop);
    }

    @Test
    void fetch() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {

        HttpGet request = new HttpGet(baseUrl + "/");
        request.addHeader("User-agent", "test.marginalia.nu");
        request.addHeader("Accept-Encoding", "gzip");

        var result = assertInstanceOf(HttpFetchResult.ResultOk.class,
                client.fetch(httpClient, new DomainCookies(), request));
        assertEquals("<!doctype html><html><body>Test document</body></html>",
                new String(result.bytes(), StandardCharsets.UTF_8));

        Map<String, String> sampleData = new HashMap<>();
        try (var warcReader = new WarcReader(fileNameWarc)) {
            warcReader.forEach(record -> {
                if (record instanceof WarcRequest req) {
                    sampleData.put(record.type(), req.target());
                }
                if (record instanceof WarcResponse rsp) {
                    sampleData.put(record.type(), rsp.target());
                }
            });
        }

        assertEquals(baseUrl + "/", sampleData.get("request"));
        assertEquals(baseUrl + "/", sampleData.get("response"));
    }

    @Test
    public void flagAsSkipped() throws IOException, URISyntaxException {

        try (var recorder = new WarcRecorder(fileNameWarc)) {
            recorder.writeReferenceCopy(new EdgeUrl("https://www.marginalia.nu/"),
                    new DomainCookies(),
                    "text/html",
                    200,
                    "<?doctype html><html><body>test</body></html>".getBytes(),
                    null,
                    ContentTags.empty());
        }

        try (var reader = new WarcReader(fileNameWarc)) {
            for (var record : reader) {
                if (record instanceof WarcResponse rsp) {
                    assertEquals("https://www.marginalia.nu/", rsp.target());
                    assertEquals("text/html", rsp.contentType().type());
                    assertEquals(200, rsp.http().status());
                    assertEquals("1", rsp.http().headers().first("X-Cookies").orElse(null));
                }
            }
        }
    }

    @Test
    public void flagAsSkippedNullBody() throws IOException, URISyntaxException {

        try (var recorder = new WarcRecorder(fileNameWarc)) {
            recorder.writeReferenceCopy(new EdgeUrl("https://www.marginalia.nu/"),
                    new DomainCookies(),
                    "text/html",
                    200,
                    null,
                    null, ContentTags.empty());
        }

    }

    @Test
    public void testSaveImport() throws URISyntaxException, IOException {
        try (var recorder = new WarcRecorder(fileNameWarc)) {
            recorder.writeReferenceCopy(new EdgeUrl("https://www.marginalia.nu/"),
                    new DomainCookies(),
                    "text/html",
                    200,
                    "<?doctype html><html><body>test</body></html>".getBytes(),
                    null, ContentTags.empty());
        }

        try (var reader = new WarcReader(fileNameWarc)) {
            WarcXResponseReference.register(reader);

            for (var record : reader) {
                System.out.println(record.type());
                System.out.println(record.getClass().getSimpleName());
                if (record instanceof WarcXResponseReference rsp) {
                    assertEquals("https://www.marginalia.nu/", rsp.target());
                }
            }
        }

    }

    @Test
    public void testConvertToParquet() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        HttpGet request1 = new HttpGet(baseUrl + "/");
        request1.addHeader("User-agent", "test.marginalia.nu");
        request1.addHeader("Accept-Encoding", "gzip");

        client.fetch(httpClient, new DomainCookies(), request1);

        HttpGet request2 = new HttpGet(baseUrl + "/log/");
        request2.addHeader("User-agent", "test.marginalia.nu");
        request2.addHeader("Accept-Encoding", "gzip");

        client.fetch(httpClient, new DomainCookies(), request2);

        HttpGet request3 = new HttpGet(baseUrl + "/sanic.png");
        request3.addHeader("User-agent", "test.marginalia.nu");
        request3.addHeader("Accept-Encoding", "gzip");

        client.fetch(httpClient, new DomainCookies(), request3);

        HttpGet request4 = new HttpGet(baseUrl + "/test.pdf");
        request4.addHeader("User-agent", "test.marginalia.nu");
        request4.addHeader("Accept-Encoding", "gzip");

        client.fetch(httpClient, new DomainCookies(), request4);

        SlopCrawlDataRecord.convertWarc(
                "www.marginalia.nu",
                new UserAgent("test", "test"),
                fileNameWarc,
                fileNameSlop);

        List<String> urls;
        try (var stream = SerializableCrawlDataStream.openDataStream(fileNameSlop)) {
            urls = stream.docsAsList().stream().map(doc -> doc.url.toString()).toList();
        }

        assertEquals(3, urls.size());
        assertEquals(baseUrl + "/", urls.get(0));
        assertEquals(baseUrl + "/log/", urls.get(1));
        // sanic.png gets filtered out for its bad mime type
        assertEquals(baseUrl + "/test.pdf", urls.get(2));

    }

}