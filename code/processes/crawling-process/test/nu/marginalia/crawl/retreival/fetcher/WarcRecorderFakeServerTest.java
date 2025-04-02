package nu.marginalia.crawl.retreival.fetcher;

import com.sun.net.httpserver.HttpServer;
import nu.marginalia.crawl.fetcher.Cookies;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import org.junit.jupiter.api.*;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRequest;
import org.netpreserve.jwarc.WarcResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag("slow")
class WarcRecorderFakeServerTest {
    static HttpServer server;

    @BeforeAll
    public static void setUpAll() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 14510), 10);
        server.createContext("/fast", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, "<html><body>hello</body></html>".length());

            try (var os = exchange.getResponseBody()) {
                os.write("<html><body>hello</body></html>".getBytes());
                os.flush();
            }
            exchange.close();
        });

        server.createContext("/slow", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, "<html><body>hello</body></html>:D".length());

            try (var os = exchange.getResponseBody()) {
                os.write("<html><body>hello</body></html>".getBytes());
                os.flush();
                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                os.write(":D".getBytes());
                os.flush();
            }
            exchange.close();
        });

        server.start();
    }

    @AfterAll
    public static void tearDownAll() {
        server.stop(0);
    }

    Path fileNameWarc;
    Path fileNameParquet;
    WarcRecorder client;

    HttpClient httpClient;
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = HttpClient.newBuilder().build();

        fileNameWarc = Files.createTempFile("test", ".warc");
        fileNameParquet = Files.createTempFile("test", ".parquet");

        client = new WarcRecorder(fileNameWarc, new Cookies());
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
        Files.delete(fileNameWarc);
    }

    @Test
    void fetchFast() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        client.fetch(httpClient,
                HttpRequest.newBuilder()
                        .uri(new java.net.URI("http://localhost:14510/fast"))
                        .timeout(Duration.ofSeconds(1))
                        .header("User-agent", "test.marginalia.nu")
                        .header("Accept-Encoding", "gzip")
                        .GET().build()
        );

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

        System.out.println(sampleData);
    }

    @Test
    void fetchSlow() throws NoSuchAlgorithmException, IOException, URISyntaxException, InterruptedException {
        Instant start = Instant.now();
        client.fetch(httpClient,
                HttpRequest.newBuilder()
                        .uri(new java.net.URI("http://localhost:14510/slow"))
                        .timeout(Duration.ofSeconds(1))
                        .header("User-agent", "test.marginalia.nu")
                        .header("Accept-Encoding", "gzip")
                        .GET().build()
        );
        Instant end = Instant.now();

        Map<String, String> sampleData = new HashMap<>();
        try (var warcReader = new WarcReader(fileNameWarc)) {
            warcReader.forEach(record -> {
                if (record instanceof WarcRequest req) {
                    sampleData.put(record.type(), req.target());
                }
                if (record instanceof WarcResponse rsp) {
                    sampleData.put(record.type(), rsp.target());
                    System.out.println(rsp.target());
                }
            });
        }

        System.out.println(sampleData);

        // Timeout is set to 1 second, but the server will take 5 seconds to respond, so we expect the request to take 1s and change
        // before it times out.
        Assertions.assertTrue(Duration.between(start, end).toMillis() < 2000, "Request should take less than 2 seconds");
    }

}