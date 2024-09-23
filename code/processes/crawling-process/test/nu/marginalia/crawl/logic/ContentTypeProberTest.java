package nu.marginalia.crawl.logic;

import com.sun.net.httpserver.HttpServer;
import nu.marginalia.model.EdgeUrl;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ContentTypeProberTest {

    private static int port;
    private static HttpServer server;
    private static OkHttpClient client;

    static EdgeUrl htmlEndpoint;
    static EdgeUrl htmlRedirEndpoint;
    static EdgeUrl binaryEndpoint;
    static EdgeUrl timeoutEndpoint;

    @BeforeEach
    void setUp() throws IOException  {
        Random r = new Random();
        port = r.nextInt(10000) + 8000;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 10);

        server.createContext("/html", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/redir", exchange -> {
            exchange.getResponseHeaders().add("Location", "/html");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        server.createContext("/bin", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/binary");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.createContext("/timeout", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            exchange.getResponseHeaders().add("Content-Type", "application/binary");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        server.start();

        htmlEndpoint = EdgeUrl.parse("http://localhost:" + port + "/html").get();
        binaryEndpoint = EdgeUrl.parse("http://localhost:" + port + "/bin").get();
        timeoutEndpoint = EdgeUrl.parse("http://localhost:" + port + "/timeout").get();
        htmlRedirEndpoint = EdgeUrl.parse("http://localhost:" + port + "/redir").get();

        client = new OkHttpClient.Builder()
                .readTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .connectTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    @Test
    void probeContentTypeOk() {
        ContentTypeProber.ContentTypeProbeResult result = new ContentTypeProber("test", client)
                .probeContentType(htmlEndpoint);

        System.out.println(result);

        assertEquals(result, new ContentTypeProber.ContentTypeProbeResult.Ok(htmlEndpoint));
    }

    @Test
    void probeContentTypeRedir() {
        ContentTypeProber.ContentTypeProbeResult result = new ContentTypeProber("test", client)
                .probeContentType(htmlRedirEndpoint);

        System.out.println(result);

        assertEquals(result, new ContentTypeProber.ContentTypeProbeResult.Ok(htmlEndpoint));
    }

    @Test
    void probeContentTypeBad() {
        ContentTypeProber.ContentTypeProbeResult result = new ContentTypeProber("test", client)
                .probeContentType(binaryEndpoint);

        System.out.println(result);

        assertInstanceOf(ContentTypeProber.ContentTypeProbeResult.BadContentType.class, result);
    }

    @Test
    void probeContentTypeTimeout() {
        ContentTypeProber.ContentTypeProbeResult result = new ContentTypeProber("test", client)
                .probeContentType(timeoutEndpoint);

        System.out.println(result);

        assertInstanceOf(ContentTypeProber.ContentTypeProbeResult.Timeout.class, result);
    }
}