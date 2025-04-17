package nu.marginalia.crawl.retreival.fetcher;

import com.sun.net.httpserver.HttpServer;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.*;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRequest;
import org.netpreserve.jwarc.WarcResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // This endpoint will finish sending the response immediately
        server.createContext("/fast", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, "<html><body>hello</body></html>".length());

            try (var os = exchange.getResponseBody()) {
                os.write("<html><body>hello</body></html>".getBytes());
                os.flush();
            }
            exchange.close();
        });

        // This endpoint will take 10 seconds to finish sending the response,
        // which should trigger a timeout in the client
        server.createContext("/slow", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, "<html><body>hello</body></html>:D".length());

            try (var os = exchange.getResponseBody()) {
                os.write("<html><body>hello</body></html>".getBytes());
                os.flush();
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                os.write(":".getBytes());
                os.flush();
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                os.write("D".getBytes());
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
        httpClient = HttpClients.createDefault();

        fileNameWarc = Files.createTempFile("test", ".warc");
        fileNameParquet = Files.createTempFile("test", ".parquet");

        client = new WarcRecorder(fileNameWarc, new BasicCookieStore());
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.close();
        Files.delete(fileNameWarc);
    }

    @Test
    public void fetchFast() throws Exception {
        HttpGet request = new HttpGet("http://localhost:14510/fast");
        request.addHeader("User-agent", "test.marginalia.nu");
        request.addHeader("Accept-Encoding", "gzip");
        client.fetch(httpClient, request);

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
    public void fetchSlow() throws Exception {
        Instant start = Instant.now();

        HttpGet request = new HttpGet("http://localhost:14510/slow");
        request.addHeader("User-agent", "test.marginalia.nu");
        request.addHeader("Accept-Encoding", "gzip");

        client.fetch(httpClient,
                request,
                Duration.ofSeconds(1)
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

        System.out.println(
                Files.readString(fileNameWarc));
        System.out.println(sampleData);

        // Timeout is set to 1 second, but the server will take 5 seconds to respond,
        // so we expect the request to take 1s and change before it times out.

        Assertions.assertTrue(Duration.between(start, end).toMillis() < 3000);
    }

}